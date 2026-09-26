/*
 * Copyright ConsenSys AG.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.BlockAddedEvent;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.PeerReputation;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PeerTransactionTrackerTest {
  private final SilPeers silPeers = mock(SilPeers.class);
  private final SilScheduler silScheduler = new DeterministicEthScheduler();
  private final SilPeer silPeer1 = mockPeer();
  private final SilPeer silPeer2 = mockPeer();
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();
  private final PeerTransactionTracker tracker =
      new PeerTransactionTracker(TransactionPoolConfiguration.DEFAULT, silPeers, silScheduler);
  private final PeerTransactionTracker forgetfulTracker =
      new PeerTransactionTracker(
          ImmutableTransactionPoolConfiguration.builder()
              .unstable(
                  ImmutableTransactionPoolConfiguration.Unstable.builder()
                      .peerTrackerForgetEvictedTxs(true)
                      .build())
              .build(),
          silPeers,
          silScheduler);
  private final PeerTransactionTracker shortMemoryTracker =
      new PeerTransactionTracker(
          ImmutableTransactionPoolConfiguration.builder()
              .unstable(
                  ImmutableTransactionPoolConfiguration.Unstable.builder()
                      .maxTrackedSeenTxs(2)
                      .build())
              .build(),
          silPeers,
          silScheduler);

  @BeforeEach
  void setUp() {
    when(silPeers.getMaxPeers()).thenReturn(25);
    when(silPeer1.isDisconnected()).thenReturn(false);
    when(silPeer2.isDisconnected()).thenReturn(false);
    tracker.onPeerConnected(silPeer1);
    tracker.onPeerConnected(silPeer2);
    forgetfulTracker.onPeerConnected(silPeer1);
    shortMemoryTracker.onPeerConnected(silPeer1);
  }

  @Test
  public void shouldTrackTransactionsToSendToPeer() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1));
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction2));
    tracker.addToPeerSendQueue(silPeer2, List.of(transaction3));

    assertThat(claimAllTransactionsToSend(tracker, silPeer1))
        .containsOnly(transaction1, transaction2);
    assertThat(claimAllTransactionsToSend(tracker, silPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldTrackSeenTransactionStatePerPeer() {
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction2.getHash()));

    // tx2 marked as seen only for peer1
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction2)).isTrue();
    // not for peer2
    assertThat(tracker.hasPeerSeenTransaction(silPeer2, transaction2)).isFalse();
    // tx1 not seen for either peer
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction1)).isFalse();
  }

  @Test
  public void shouldStopTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    forgetfulTracker.markTransactionsAsSeen(silPeer1, List.of(transaction2.getHash()));

    assertThat(forgetfulTracker.alreadySeenTransaction(transaction2.getHash())).isTrue();

    forgetfulTracker.onTransactionDropped(transaction2, createRemovalReason(true, false));

    assertThat(forgetfulTracker.alreadySeenTransaction(transaction2.getHash())).isFalse();
  }

  @Test
  public void shouldKeepTrackingSeenTransactionsWhenNotForgettingEvenIfRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction2.getHash()));

    assertThat(tracker.alreadySeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(true, false));

    assertThat(tracker.alreadySeenTransaction(transaction2.getHash())).isTrue();
  }

  @Test
  public void shouldRemoveTheLastRecentSeenTransactionWhenTheCacheIsFull() {
    shortMemoryTracker.markTransactionsAsSeen(
        silPeer1, List.of(transaction1.getHash(), transaction2.getHash()));

    assertThat(shortMemoryTracker.alreadySeenTransaction(transaction1.getHash())).isTrue();
    assertThat(shortMemoryTracker.alreadySeenTransaction(transaction2.getHash())).isTrue();

    // now the cache is full and the last recent entry is the transaction1
    // so it should be evicted when inserting transaction3
    shortMemoryTracker.markTransactionsAsSeen(silPeer1, List.of(transaction3.getHash()));

    assertThat(shortMemoryTracker.alreadySeenTransaction(transaction1.getHash())).isFalse();
    assertThat(shortMemoryTracker.alreadySeenTransaction(transaction2.getHash())).isTrue();
    assertThat(shortMemoryTracker.alreadySeenTransaction(transaction3.getHash())).isTrue();
  }

  @Test
  public void shouldKeepTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction2.getHash()));

    assertThat(tracker.alreadySeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(false, false));

    assertThat(tracker.alreadySeenTransaction(transaction2.getHash())).isTrue();
  }

  @Test
  public void shouldTrackSeenTransactionStateForCollectionPerPeer() {
    tracker.markTransactionsAsSeen(
        silPeer1, List.of(transaction1.getHash(), transaction2.getHash()));

    // both tx1 and tx2 marked as seen for peer1
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction1)).isTrue();
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction2)).isTrue();
    // not for peer2
    assertThat(tracker.hasPeerSeenTransaction(silPeer2, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(silPeer2, transaction2)).isFalse();
  }

  @Test
  public void shouldClearDataWhenPeerDisconnects() {
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction1.getHash()));

    tracker.addToPeerSendQueue(silPeer1, List.of(transaction2));
    tracker.addToPeerSendQueue(silPeer2, List.of(transaction3));

    when(silPeers.streamAllConnectedPeers()).thenReturn(Stream.of(silPeer2));
    tracker.onDisconnect(silPeer1);

    // peer1's send queue cleared after disconnect
    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).isEmpty();
    // peer2 is unaffected
    assertThat(claimAllTransactionsToSend(tracker, silPeer2)).containsOnly(transaction3);

    // Should have cleared data that silPeer1 has already seen transaction1
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1));

    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).containsOnly(transaction1);
  }

  @Test
  public void shouldClearDataForAllDisconnectedPeers() {
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction1.getHash()));
    tracker.markTransactionsAsSeen(silPeer2, List.of(transaction2.getHash()));

    when(silPeers.streamAllConnectedPeers()).thenReturn(Stream.of(silPeer2));
    tracker.onDisconnect(silPeer1);

    // false because tracker removed for silPeer1
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(silPeer2, transaction2)).isTrue();

    // simulate a concurrent interaction: peer1 reconnects and is re-registered,
    // then immediately marks a transaction as seen before the tracker is fully reconciled
    tracker.onPeerConnected(silPeer1);
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction1.getHash()));
    // silPeer1 is here again, due to the above interaction with the tracker
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction1)).isTrue();

    // disconnection of silPeers2 will reconcile the tracker, removing also all the other
    // disconnected peers
    when(silPeers.streamAllConnectedPeers()).thenReturn(Stream.of());
    tracker.onDisconnect(silPeer2);

    // since no peers are connected, all the transaction trackers have been removed
    assertThat(tracker.hasPeerSeenTransaction(silPeer1, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(silPeer2, transaction2)).isFalse();
  }

  private RemovalReason createRemovalReason(
      final boolean stopTracking, final boolean stopBroadcasting) {
    return new RemovalReason() {

      @Override
      public String label() {
        return "";
      }

      @Override
      public boolean stopTracking() {
        return stopTracking;
      }

      @Override
      public boolean stopBroadcasting() {
        return stopBroadcasting;
      }
    };
  }

  @Test
  public void shouldRemoveConfirmedTransactionsFromAllQueuesOnBlockAdded() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1, transaction2));
    tracker.addToPeerAnnouncementsSendQueue(silPeer1, List.of(transaction1, transaction3));
    tracker.receivedAnnouncements(silPeer2, TransactionAnnouncement.create(List.of(transaction1)));

    final Block block =
        generator.block(BlockDataGenerator.BlockOptions.create().addTransaction(transaction1));
    tracker.onBlockAdded(BlockAddedEvent.createForHeadAdvancement(block, List.of(), List.of()));

    // transaction1 removed from full-tx send queue
    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).containsOnly(transaction2);
    // transaction1 removed from announcements send queue
    assertThat(claimAllAnnouncementsToSend(tracker, silPeer1)).containsOnly(transaction3);
    // transaction1 removed from peer2's announcement request queue
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer2, 10, 100_000L)).isEmpty();
    // transaction1 recorded as recently confirmed
    assertThat(tracker.alreadySeenTransaction(transaction1.getHash())).isTrue();
    // unconfirmed transactions not affected
    assertThat(tracker.alreadySeenTransaction(transaction2.getHash())).isFalse();
  }

  @Test
  public void shouldNotRemoveTransactionFromSendQueuesWhenStopBroadcastingIsFalse() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1));
    tracker.addToPeerAnnouncementsSendQueue(silPeer1, List.of(transaction1));

    // stopBroadcasting=false (e.g. RECONCILED): queues must not be cleared
    tracker.onTransactionDropped(transaction1, createRemovalReason(false, false));

    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).containsOnly(transaction1);
    assertThat(claimAllAnnouncementsToSend(tracker, silPeer1)).containsOnly(transaction1);
  }

  @Test
  public void shouldRemoveTransactionFromSendQueuesWhenStopBroadcastingIsTrue() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1));
    tracker.addToPeerAnnouncementsSendQueue(silPeer1, List.of(transaction1));

    tracker.onTransactionDropped(transaction1, createRemovalReason(false, true));

    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).isEmpty();
    assertThat(claimAllAnnouncementsToSend(tracker, silPeer1)).isEmpty();
  }

  @Test
  public void claimAnnouncementsToRequestFromPeer_shouldLimitByCumulativeSize() {
    // MAX_SIZE check is at the START of each iteration, using the size accumulated so far.
    // With 3 announcements of 600KB and maxSize=1MB:
    //   iter 1: cumulative=0 < 1MB  → claim ann1 → cumulative=600KB
    //   iter 2: cumulative=600KB < 1MB → claim ann2 → cumulative=1200KB
    //   iter 3: cumulative=1200KB ≥ 1MB → exit
    // So the first call returns [ann1, ann2]; ann3 stays queued.
    final long annSize = 600_000L;
    final long maxSize = 1_000_000L;

    final TransactionAnnouncement ann1 =
        new TransactionAnnouncement(transaction1.getHash(), transaction1.getType(), annSize);
    final TransactionAnnouncement ann2 =
        new TransactionAnnouncement(transaction2.getHash(), transaction2.getType(), annSize);
    final TransactionAnnouncement ann3 =
        new TransactionAnnouncement(transaction3.getHash(), transaction3.getType(), annSize);

    tracker.receivedAnnouncements(silPeer1, List.of(ann1, ann2, ann3));

    final List<TransactionAnnouncement> firstBatch =
        tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 10, maxSize);
    assertThat(firstBatch).containsExactly(ann1, ann2);

    // ann3 is still in the queue; a second claim should return it
    final List<TransactionAnnouncement> secondBatch =
        tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 10, maxSize);
    assertThat(secondBatch).containsExactly(ann3);
  }

  @Test
  public void receivedAnnouncements_shouldReturnOnlyFreshAnnouncements() {
    // Pre-mark transaction1 as seen via a full-transaction receive
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction1.getHash()));

    final var fresh =
        tracker.receivedAnnouncements(
            silPeer1,
            TransactionAnnouncement.create(List.of(transaction1, transaction2, transaction3)));

    // transaction1 already seen — excluded from the fresh list
    assertThat(fresh)
        .extracting(TransactionAnnouncement::hash)
        .containsExactlyInAnyOrder(transaction2.getHash(), transaction3.getHash());

    // Only fresh ones are enqueued for retrieval
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 10, Long.MAX_VALUE))
        .extracting(TransactionAnnouncement::hash)
        .containsExactlyInAnyOrder(transaction2.getHash(), transaction3.getHash());
  }

  @Test
  public void markTransactionsAsSeen_shouldRemoveFromAllPeersRequestQueues() {
    // Both peers have transaction1 in their request queues
    tracker.receivedAnnouncements(
        silPeer1, TransactionAnnouncement.create(List.of(transaction1, transaction2)));
    tracker.receivedAnnouncements(
        silPeer2, TransactionAnnouncement.create(List.of(transaction1, transaction3)));

    // Mark transaction1 as seen (e.g. received as a full tx from silPeer1)
    tracker.markTransactionsAsSeen(silPeer1, List.of(transaction1.getHash()));

    // transaction1 must be removed from BOTH peers' request queues
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 10, Long.MAX_VALUE))
        .extracting(TransactionAnnouncement::hash)
        .containsOnly(transaction2.getHash())
        .doesNotContain(transaction1.getHash());
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer2, 10, Long.MAX_VALUE))
        .extracting(TransactionAnnouncement::hash)
        .containsOnly(transaction3.getHash())
        .doesNotContain(transaction1.getHash());
  }

  @Test
  public void shouldRemoveConfirmedTransactionsFromAllQueuesOnChainReorg() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1, transaction2));
    tracker.receivedAnnouncements(silPeer2, TransactionAnnouncement.create(List.of(transaction1)));

    final Block block =
        generator.block(BlockDataGenerator.BlockOptions.create().addTransaction(transaction1));
    tracker.onBlockAdded(
        BlockAddedEvent.createForChainReorg(
            block,
            List.of(transaction1),
            List.of(),
            List.of(),
            List.of(),
            block.getHeader().getParentHash()));

    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).containsOnly(transaction2);
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer2, 10, Long.MAX_VALUE)).isEmpty();
    assertThat(tracker.alreadySeenTransaction(transaction1.getHash())).isTrue();
  }

  @Test
  public void shouldIgnoreForkBlockEvents() {
    tracker.addToPeerSendQueue(silPeer1, List.of(transaction1));
    tracker.receivedAnnouncements(silPeer1, TransactionAnnouncement.create(List.of(transaction2)));

    final Block block =
        generator.block(BlockDataGenerator.BlockOptions.create().addTransaction(transaction1));
    tracker.onBlockAdded(BlockAddedEvent.createForFork(block));

    // FORK events must not remove anything
    assertThat(claimAllTransactionsToSend(tracker, silPeer1)).containsOnly(transaction1);
    assertThat(tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 10, Long.MAX_VALUE))
        .isNotEmpty();
    assertThat(tracker.alreadySeenTransaction(transaction1.getHash())).isFalse();
  }

  @Test
  public void claimAnnouncementsToRequestFromPeer_shouldLimitByMaxHashes() {
    final List<Transaction> transactions = new ArrayList<>(generator.transactions(5));
    tracker.receivedAnnouncements(silPeer1, TransactionAnnouncement.create(transactions));

    // Claim at most 3 at a time
    final List<TransactionAnnouncement> firstBatch =
        tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 3, Long.MAX_VALUE);
    assertThat(firstBatch).hasSize(3);

    // The remaining 2 are still queued (not in-progress)
    final List<TransactionAnnouncement> secondBatch =
        tracker.claimAnnouncementsToRequestFromPeer(silPeer1, 3, Long.MAX_VALUE);
    assertThat(secondBatch).hasSize(2);

    // All 5 unique hashes are covered across both batches
    assertThat(
            Stream.concat(firstBatch.stream(), secondBatch.stream())
                .map(TransactionAnnouncement::hash))
        .containsExactlyInAnyOrderElementsOf(
            transactions.stream().map(Transaction::getHash).toList());
  }

  @Test
  public void receivedAnnouncements_shouldAcceptBatchLargerThanQueueCap() {
    final int queueCap = 4;
    final PeerTransactionTracker smallQueueTracker =
        new PeerTransactionTracker(
            ImmutableTransactionPoolConfiguration.builder()
                .unstable(
                    ImmutableTransactionPoolConfiguration.Unstable.builder()
                        .maxSendQueueSizePerPeer(queueCap)
                        .build())
                .build(),
            silPeers,
            silScheduler);
    smallQueueTracker.onPeerConnected(silPeer1);

    final List<Transaction> transactions = new ArrayList<>(generator.transactions(queueCap * 2));

    smallQueueTracker.receivedAnnouncements(silPeer1, TransactionAnnouncement.create(transactions));

    final List<TransactionAnnouncement> claimed =
        smallQueueTracker.claimAnnouncementsToRequestFromPeer(
            silPeer1, queueCap * 2, Long.MAX_VALUE);
    assertThat(claimed).hasSize(queueCap);
  }

  private List<Transaction> claimAllAnnouncementsToSend(
      final PeerTransactionTracker tracker, final SilPeer peer) {
    final List<Transaction> result = new ArrayList<>();
    Transaction tx;
    while ((tx = tracker.claimAnnouncementToSendToPeer(peer)) != null) {
      result.add(tx);
    }
    return result;
  }

  private List<Transaction> claimAllTransactionsToSend(
      final PeerTransactionTracker tracker, final SilPeer peer) {
    final List<Transaction> result = new ArrayList<>();
    Transaction tx;
    while ((tx = tracker.claimTransactionToSendToPeer(peer)) != null) {
      result.add(tx);
    }
    return result;
  }

  private SilPeer mockPeer() {
    final SilPeer peer = mock(SilPeer.class);
    final ChainState chainState = new ChainState();
    chainState.updateHeightEstimate(0);
    chainState.statusReceived(Hash.EMPTY, Difficulty.of(0));
    when(peer.chainState()).thenReturn(chainState);
    when(peer.getReputation()).thenReturn(new PeerReputation());
    PeerConnection connection = mock(PeerConnection.class);
    when(peer.getConnection()).thenReturn(connection);
    return peer;
  }
}
