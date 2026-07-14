/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.sila.sil.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.TransactionType.BLOB;
import static org.hyperledger.besu.sila.sil.transactions.PendingTransaction.toTransactionList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.PeerReputation;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransactionBroadcasterTest {
  private static final Long FIXED_RANDOM_SEED = 0L;
  @Mock private SilContext silContext;
  @Mock private SilPeers silPeers;
  @Mock private SilScheduler silScheduler;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private TransactionsMessageSender transactionsMessageSender;
  @Mock private NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;

  private final SilPeer silPeer = mockPeer();
  private final SilPeer silPeer2 = mockPeer();
  private final SilPeer silPeer3 = mockPeer();
  private final BlockDataGenerator generator = new BlockDataGenerator();

  private TransactionBroadcaster txBroadcaster;
  private ArgumentCaptor<Runnable> sendTaskCapture;

  @BeforeEach
  public void setUp() {
    sendTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(silScheduler).scheduleSyncWorkerTask(sendTaskCapture.capture());

    when(silContext.getSilPeers()).thenReturn(silPeers);
    when(silContext.getScheduler()).thenReturn(silScheduler);

    // we use the fixed random seed to have a predictable shuffle of peers
    txBroadcaster =
        new TransactionBroadcaster(
            silContext,
            transactionTracker,
            transactionsMessageSender,
            newPooledTransactionHashesMessageSender,
            FIXED_RANDOM_SEED);
  }

  @Test
  public void doNotRelayTransactionsWhenPoolIsEmpty() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(0, 0);

    txBroadcaster.relayTransactionPoolTo(silPeer, pendingTxs);

    verifyNothingSent();
  }

  @Test
  public void relayTransactionHashesFromPool() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(1, 1);
    List<Transaction> txs = toTransactionList(pendingTxs);

    txBroadcaster.relayTransactionPoolTo(silPeer, pendingTxs);

    verifyTransactionAddedToPeerHashSendingQueue(silPeer, txs);

    sendTaskCapture.getValue().run();

    verify(newPooledTransactionHashesMessageSender).sendTransactionAnnouncementsToPeer(silPeer);
    verifyNoInteractions(transactionsMessageSender);
  }

  @Test
  public void onTransactionsAddedWithNoPeersDoesNothing() {
    when(silPeers.peerCount()).thenReturn(0);

    txBroadcaster.onTransactionsAdded(toTransactionList(setupTransactionPool(1, 1)));

    verifyNothingSent();
  }

  /**
   * Regression test for the race condition that caused an IndexOutOfBoundsException in
   * TransactionBroadcaster.onTransactionsAdded.
   *
   * <p>The race: {@code peerCount()} is called first to calculate {@code
   * numPeersToSendFullTransactions = sqrt(peerCount)}. Then {@code streamAvailablePeers()} is
   * called to get the actual peer list. Between these two calls, peers can disconnect, so {@code
   * streamAvailablePeers()} may return fewer peers than {@code peerCount()} indicated.
   *
   * <p>Before the fix, {@code peers.subList(0, numPeersToSendFullTransactions)} would throw {@link
   * IndexOutOfBoundsException} when {@code numPeersToSendFullTransactions > peers.size()}. The fix
   * clamps the index with {@code Math.min(numPeersToSendFullTransactions, peers.size())}.
   */
  @Test
  public void onTransactionsAddedDoesNotThrowWhenPeersDisconnectBetweenCountAndStream() {
    // Simulate: peerCount() returns 9 (so numPeersToSendFullTransactions = sqrt(9) = 3),
    // but by the time streamAvailablePeers() is called, only 2 peers remain.
    // Before the fix this triggered: IndexOutOfBoundsException from subList(0, 3) on a list of 2.
    when(silPeers.peerCount()).thenReturn(9);
    when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(silPeer, silPeer2).map(SilPeerImmutableAttributes::from));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    // Must not throw IndexOutOfBoundsException
    txBroadcaster.onTransactionsAdded(txs);

    // All available peers should receive the transactions (as full or hash-only)
    sendTaskCapture.getAllValues().forEach(Runnable::run);
  }

  @Test
  public void onTransactionsAddedWithOnly2PeersSendFullTransactions() {
    when(silPeers.peerCount()).thenReturn(2);
    when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(silPeer, silPeer2).map(SilPeerImmutableAttributes::from));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [silPeer, silPeer2]
    // so silPeer is full transaction peer and silPeer2 is hash only peer
    verifyTransactionAddedToPeerSendingQueue(silPeer, txs);
    verifyTransactionAddedToPeerHashSendingQueue(silPeer2, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender).sendTransactionsToPeer(silPeer);
    verify(newPooledTransactionHashesMessageSender).sendTransactionAnnouncementsToPeer(silPeer2);
  }

  @Test
  public void onTransactionsAddedWithMorePeersSendFullTransactionsAndTransactionHashes() {
    when(silPeers.peerCount()).thenReturn(3);
    when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(silPeer, silPeer2, silPeer3).map(SilPeerImmutableAttributes::from));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [silPeer3, silPeer2, silPeer]
    // so silPeer and silPeer2 are moved to the mixed broadcast list
    verifyTransactionAddedToPeerSendingQueue(silPeer3, txs);
    verifyTransactionAddedToPeerSendingQueue(silPeer2, txs);
    verifyTransactionAddedToPeerHashSendingQueue(silPeer, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(2)).sendTransactionsToPeer(any(SilPeer.class));
    verify(newPooledTransactionHashesMessageSender)
        .sendTransactionAnnouncementsToPeer(any(SilPeer.class));
  }

  @Test
  public void onTransactionsAddedWithMixedTransactionBroadcastKind() {
    List<SilPeer> peers = List.of(silPeer, silPeer2, silPeer3);

    when(silPeers.peerCount()).thenReturn(3);
    when(silPeers.streamAvailablePeers())
        .thenReturn(peers.stream().map(SilPeerImmutableAttributes::from));

    // 1 full broadcast transaction type
    // 1 hash only broadcast transaction type
    List<Transaction> fullBroadcastTxs =
        toTransactionList(setupTransactionPool(TransactionType.SIP1559, 0, 1));
    List<Transaction> hashBroadcastTxs = toTransactionList(setupTransactionPool(BLOB, 0, 1));

    List<Transaction> mixedTxs = new ArrayList<>(fullBroadcastTxs);
    mixedTxs.addAll(hashBroadcastTxs);

    txBroadcaster.onTransactionsAdded(mixedTxs);
    // the shuffled hash only peer list is always:
    // [silPeer3, silPeer2, silPeer]
    // so silPeer3 and silPeer2 are full transaction peers
    verifyTransactionAddedToPeerHashSendingQueue(silPeer, mixedTxs);
    verifyTransactionAddedToPeerHashSendingQueue(silPeer2, hashBroadcastTxs);
    verifyTransactionAddedToPeerSendingQueue(silPeer2, fullBroadcastTxs);
    verifyTransactionAddedToPeerHashSendingQueue(silPeer3, hashBroadcastTxs);
    verifyTransactionAddedToPeerSendingQueue(silPeer3, fullBroadcastTxs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(newPooledTransactionHashesMessageSender, times(3))
        .sendTransactionAnnouncementsToPeer(any(SilPeer.class));
    ArgumentCaptor<SilPeer> capPeerFullTransaction = ArgumentCaptor.forClass(SilPeer.class);
    verify(transactionsMessageSender, times(2))
        .sendTransactionsToPeer(capPeerFullTransaction.capture());
    List<SilPeer> fullTransactionPeers = new ArrayList<>(capPeerFullTransaction.getAllValues());
    assertThat(fullTransactionPeers).hasSameElementsAs(List.of(silPeer2, silPeer3));
  }

  private void verifyNothingSent() {
    verifyNoInteractions(
        transactionTracker, transactionsMessageSender, newPooledTransactionHashesMessageSender);
  }

  private Set<PendingTransaction> setupTransactionPool(
      final int numLocalTransactions, final int numRemoteTransactions) {
    Set<PendingTransaction> pendingTxs = createPendingTransactionList(numLocalTransactions, true);
    pendingTxs.addAll(createPendingTransactionList(numRemoteTransactions, false));

    return pendingTxs;
  }

  private Set<PendingTransaction> setupTransactionPool(
      final TransactionType type, final int numLocalTransactions, final int numRemoteTransactions) {
    Set<PendingTransaction> pendingTxs =
        createPendingTransactionList(type, numLocalTransactions, true);
    pendingTxs.addAll(createPendingTransactionList(type, numRemoteTransactions, false));

    return pendingTxs;
  }

  private Set<PendingTransaction> createPendingTransactionList(final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction())
        .map(tx -> local ? new PendingTransaction.Local(tx) : new PendingTransaction.Remote(tx))
        .collect(Collectors.toSet());
  }

  private Set<PendingTransaction> createPendingTransactionList(
      final TransactionType type, final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction(type))
        .map(tx -> local ? new PendingTransaction.Local(tx) : new PendingTransaction.Remote(tx))
        .collect(Collectors.toSet());
  }

  @SuppressWarnings("unchecked")
  private void verifyTransactionAddedToPeerSendingQueue(
      final SilPeer peer, final Collection<Transaction> transactions) {

    ArgumentCaptor<List<Transaction>> trackedTransactions = ArgumentCaptor.forClass(List.class);
    verify(transactionTracker).addToPeerSendQueue(eq(peer), trackedTransactions.capture());
    assertThat(trackedTransactions.getValue()).containsExactlyInAnyOrderElementsOf(transactions);
  }

  @SuppressWarnings("unchecked")
  private void verifyTransactionAddedToPeerHashSendingQueue(
      final SilPeer peer, final Collection<Transaction> transactions) {

    ArgumentCaptor<List<Transaction>> trackedTransactions = ArgumentCaptor.forClass(List.class);
    verify(transactionTracker)
        .addToPeerAnnouncementsSendQueue(eq(peer), trackedTransactions.capture());
    assertThat(trackedTransactions.getValue()).containsExactlyInAnyOrderElementsOf(transactions);
  }

  private SilPeer mockPeer() {
    SilPeer silPeer = mock(SilPeer.class);
    ChainState chainState = mock(ChainState.class);

    when(silPeer.chainState()).thenReturn(chainState);
    when(chainState.getEstimatedHeight()).thenReturn(0L);
    when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));
    when(silPeer.getReputation()).thenReturn(new PeerReputation());
    PeerConnection connection = mock(PeerConnection.class);
    when(silPeer.getConnection()).thenReturn(connection);
    return silPeer;
  }
}
