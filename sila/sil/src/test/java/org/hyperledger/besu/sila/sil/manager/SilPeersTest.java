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
package org.hyperledger.besu.sila.sil.manager;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.p2p.peers.Peer;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.sil.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.sila.sil.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.sila.sil.messages.BlockBodiesMessage;
import org.hyperledger.besu.sila.sil.sync.ChainHeadTracker;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class SilPeersTest {

  private SilProtocolManager silProtocolManager;
  private SilPeers silPeers;
  private final PeerRequest peerRequest = mock(PeerRequest.class);
  private final RequestManager.ResponseStream responseStream =
      mock(RequestManager.ResponseStream.class);

  @BeforeEach
  public void setup() throws Exception {
    when(peerRequest.sendRequest(any())).thenReturn(responseStream);
    silProtocolManager = SilProtocolManagerTestBuilder.builder().build();
    silPeers = silProtocolManager.silContext().getEthPeers();
    final ChainHeadTracker mock = mock(ChainHeadTracker.class);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(mock.getBestHeaderFromPeer(any()))
        .thenReturn(CompletableFuture.completedFuture(blockHeader));
    silPeers.setChainHeadTracker(mock);
  }

  @Test
  public void comparesPeersWithHeightAndTd() {
    // Set peerA with better height, lower td
    final SilPeerImmutableAttributes peerA =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(50), 20)
                .getEthPeer());
    final SilPeerImmutableAttributes peerB =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(100), 10)
                .getEthPeer());

    assertThat(SilPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isLessThan(0);

    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerB)).isLessThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerA)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerA)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerB)).isEqualTo(0);

    assertThat(silProtocolManager.silContext().getEthPeers().bestPeer()).contains(peerB.silPeer());
    assertThat(silProtocolManager.silContext().getEthPeers().bestPeerWithHeightEstimate())
        .contains(peerB.silPeer());
  }

  @Test
  public void comparesPeersWithTdAndNoHeight() {
    final SilPeerImmutableAttributes peerA =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(
                    silProtocolManager, Difficulty.of(100), OptionalLong.empty())
                .getEthPeer());
    final SilPeerImmutableAttributes peerB =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(
                    silProtocolManager, Difficulty.of(50), OptionalLong.empty())
                .getEthPeer());

    // Sanity check
    assertThat(peerA.estimatedChainHeight()).isEqualTo(0);
    assertThat(peerB.estimatedChainHeight()).isEqualTo(0);

    assertThat(SilPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isGreaterThan(0);

    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerA)).isLessThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerA)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerB)).isEqualTo(0);

    assertThat(silProtocolManager.silContext().getEthPeers().bestPeer()).contains(peerA.silPeer());
    assertThat(silProtocolManager.silContext().getEthPeers().bestPeerWithHeightEstimate())
        .isEmpty();
  }

  @Test
  public void shouldExecutePeerRequestImmediatelyWhenPeerIsAvailable() throws Exception {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    when(peerRequest.isEthPeerSuitable(SilPeerImmutableAttributes.from(peer.getEthPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastBusyPeerForRequest() throws Exception {
    final RespondingEthPeer idlePeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final RespondingEthPeer workingPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useRequestSlot(workingPeer.getEthPeer());

    when(peerRequest.isEthPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(idlePeer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastRecentlyUsedPeerWhenBothHaveSameNumberOfOutstandingRequests()
      throws Exception {
    final RespondingEthPeer mostRecentlyUsedPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final RespondingEthPeer leastRecentlyUsedPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useRequestSlot(mostRecentlyUsedPeer.getEthPeer());
    freeUpCapacity(mostRecentlyUsedPeer.getEthPeer());

    assertThat(leastRecentlyUsedPeer.getEthPeer().outstandingRequests())
        .isEqualTo(mostRecentlyUsedPeer.getEthPeer().outstandingRequests());

    when(peerRequest.isEthPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(leastRecentlyUsedPeer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldFailWithNoAvailablePeersWhenNoPeersConnected() {
    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verifyNoInteractions(peerRequest);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWhenNoPeerWithSufficientHeight() {
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 100);
    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 200, Optional.empty());

    verifyNoInteractions(peerRequest);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWhenAllPeersWithSufficientHeightHaveDisconnected() throws Exception {
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 100);
    final RespondingEthPeer suitablePeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(suitablePeer.getEthPeer());

    when(peerRequest.isEthPeerSuitable(SilPeerImmutableAttributes.from(suitablePeer.getEthPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 200, Optional.empty());

    verify(peerRequest, times(0)).sendRequest(suitablePeer.getEthPeer());

    assertNotDone(pendingRequest);

    suitablePeer.disconnect(DisconnectReason.TOO_MANY_PEERS);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWithPeerNotConnectedIfPeerRequestThrows() throws Exception {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    when(peerRequest.sendRequest(peer.getEthPeer())).thenThrow(new PeerNotConnected("Oh dear"));
    when(peerRequest.isEthPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());

    assertRequestFailure(pendingRequest, PeerDisconnectedException.class);
  }

  @Test
  public void shouldDelayExecutionUntilPeerHasCapacity() throws Exception {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(peer.getEthPeer());

    when(peerRequest.isEthPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getEthPeer());

    freeUpCapacity(peer.getEthPeer());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldDelayExecutionUntilPeerWithSufficientHeightHasCapacity() throws Exception {
    // Create a peer that has available capacity but not the required height
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 10);

    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    when(peerRequest.isEthPeerSuitable(Mockito.any()))
        .thenAnswer(
            (invocationOnMock) -> {
              SilPeerImmutableAttributes silPeer =
                  invocationOnMock.getArgument(0, SilPeerImmutableAttributes.class);
              return silPeer.silPeer().equals(peer.getEthPeer());
            });
    useAllAvailableCapacity(peer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getEthPeer());

    freeUpCapacity(peer.getEthPeer());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldNotExecuteAbortedRequest() throws Exception {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(peer.getEthPeer());

    when(peerRequest.isEthPeerSuitable(SilPeerImmutableAttributes.from(peer.getEthPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getEthPeer());

    pendingRequest.abort();

    freeUpCapacity(peer.getEthPeer());

    verify(peerRequest, times(0)).sendRequest(peer.getEthPeer());
    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  // We had a bug where if a peer was busy when it was disconnected, pending peer requests that were
  // *explicitly* assigned to that peer would never be attempted and thus never completed
  @Test
  public void shouldFailRequestWithBusyDisconnectedAssignedPeer() throws Exception {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = peer.getEthPeer();
    useAllAvailableCapacity(silPeer);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.of(silPeer));

    silPeer.disconnect(DisconnectReason.UNKNOWN);
    silPeers.registerDisconnect(silPeer.getConnection());

    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  @Test
  public void shouldNotFailWhenAttemptExecutionDisconnectSamePeer() throws PeerNotConnected {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = spy(peer.getEthPeer());

    // Force request to be added to pending request list
    when(silPeer.hasAvailableRequestCapacity()).thenReturn(false);

    final PendingPeerRequest pendingPeerRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.of(silPeer));

    // Force Request Attempt to cause the peer to disconnect
    when(silPeer.hasAvailableRequestCapacity())
        .thenAnswer(
            (Answer<Boolean>)
                invocation -> {
                  // Force Disconnect only on the first execution
                  if (!peer.getPeerConnection().isDisconnected()) {
                    peer.disconnect(DisconnectReason.UNKNOWN); // Force Peer to disconnect
                  }
                  return true;
                });

    // Sent Pending Requests
    silPeers.reattemptPendingPeerRequests();

    // Request should be aborted.
    assertRequestFailure(pendingPeerRequest, CancellationException.class);

    // Mock works
    assertThat(peer.getEthPeer().isDisconnected()).isTrue(); // peer is disconnected
  }

  @Test
  public void shouldNotFailWhenAttemptExecutionDisconnectAnotherPeer() throws PeerNotConnected {
    final RespondingEthPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = spy(peer.getEthPeer());

    // Force request to be added to pending request list
    when(silPeer.hasAvailableRequestCapacity()).thenReturn(false);

    final PendingPeerRequest pendingPeerRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.of(silPeer));

    final RespondingEthPeer peerToDisconnect =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    // Force Request Attempt to cause the peer to disconnect
    when(silPeer.hasAvailableRequestCapacity())
        .thenAnswer(
            (Answer<Boolean>)
                invocation -> {
                  // Force Disconnect only on the first execution
                  if (!peerToDisconnect.getPeerConnection().isDisconnected()) {
                    peerToDisconnect.disconnect(
                        DisconnectReason.UNKNOWN); // Force Peer to disconnect
                  }
                  return true;
                });

    // Sent Pending Requests
    silPeers.reattemptPendingPeerRequests();

    // Request Should Execute
    assertRequestSuccessful(pendingPeerRequest);

    // Mock works
    assertThat(peerToDisconnect.getEthPeer().isDisconnected()).isTrue(); // peer is disconnected
  }

  @Test
  public void comparesConnectionInitiationTimesWithoutOverflowingWhenFarApart() {
    final PeerConnection oldConnection = mock(PeerConnection.class);
    final PeerConnection newConnection = mock(PeerConnection.class);
    // more than Integer.MAX_VALUE milliseconds (~24.8 days) apart
    when(oldConnection.getInitiatedAt()).thenReturn(0L);
    when(newConnection.getInitiatedAt()).thenReturn(TimeUnit.DAYS.toMillis(30));

    assertThat(silPeers.compareConnectionInitiationTimes(oldConnection, newConnection))
        .isNegative();
    assertThat(silPeers.compareConnectionInitiationTimes(newConnection, oldConnection))
        .isPositive();
    assertThat(silPeers.compareConnectionInitiationTimes(oldConnection, oldConnection)).isZero();
  }

  @Test
  public void toString_hasExpectedInfo() {
    assertThat(silPeers.toString()).isEqualTo("0 SilPeers {}");

    final SilPeer peerA =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(50), 20)
            .getEthPeer();
    silPeers.registerNewConnection(peerA.getConnection(), Collections.emptyList());
    assertThat(silPeers.toString()).contains("1 SilPeers {");
    assertThat(silPeers.toString()).contains(peerA.getLoggableId());
  }

  @Test
  public void snapServersPreferredWhileSyncing() {

    silPeers.snapServerPeersNeeded(true);

    while (silPeers.peerCount() < silPeers.getMaxPeers()) {
      final SilPeer silPeer =
          SilProtocolManagerTestUtil.createPeer(
                  silProtocolManager, Difficulty.of(50), 20, false, false)
              .getEthPeer();
      assertThat(silPeers.addPeerToEthPeers(silPeer)).isTrue();
    }

    final SilPeer nonSnapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, false, false)
            .getEthPeer();

    assertThat(silPeers.addPeerToEthPeers(nonSnapServingPeer)).isFalse();
    assertThat(nonSnapServingPeer.getConnection().isDisconnected()).isTrue();

    final SilPeer snapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, true, false)
            .getEthPeer();

    assertThat(silPeers.addPeerToEthPeers(snapServingPeer)).isTrue();
    assertThat(silPeers.peerCount()).isEqualTo(silPeers.getMaxPeers());
  }

  @Test
  public void snapServersNotPreferredWhenInSync() {

    silPeers.snapServerPeersNeeded(false);

    while (silPeers.peerCount() < silPeers.getMaxPeers()) {
      final SilPeer silPeer =
          SilProtocolManagerTestUtil.createPeer(
                  silProtocolManager, Difficulty.of(50), 20, false, false)
              .getEthPeer();
      assertThat(silPeers.addPeerToEthPeers(silPeer)).isTrue();
    }

    final SilPeer snapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, true, false)
            .getEthPeer();

    assertThat(silPeers.addPeerToEthPeers(snapServingPeer)).isFalse();
    assertThat(snapServingPeer.getConnection().isDisconnected()).isTrue();
    assertThat(silPeers.peerCount()).isEqualTo(silPeers.getMaxPeers());
  }

  private void freeUpCapacity(final SilPeer silPeer) {
    MessageData message = BlockBodiesMessage.create(emptyList());
    silPeers.dispatchMessage(
        silPeer, new SilMessage(silPeer, message.wrapMessageData(BigInteger.ONE)));
    assertThat(silPeer.hasAvailableRequestCapacity()).isTrue();
  }

  private void useAllAvailableCapacity(final SilPeer peer) throws PeerNotConnected {
    while (peer.hasAvailableRequestCapacity()) {
      useRequestSlot(peer);
    }
    assertThat(peer.hasAvailableRequestCapacity()).isFalse();
  }

  private void useRequestSlot(final SilPeer peer) throws PeerNotConnected {
    peer.getBodies(singletonList(Hash.ZERO));
  }

  @SuppressWarnings("unchecked")
  private void assertRequestSuccessful(final PendingPeerRequest pendingRequest) {
    final Consumer<RequestManager.ResponseStream> onSuccess = mock(Consumer.class);
    pendingRequest.then(onSuccess, error -> fail("Request should have executed", error));
    verify(onSuccess).accept(any());
  }

  @SuppressWarnings("unchecked")
  private void assertRequestFailure(
      final PendingPeerRequest pendingRequest, final Class<? extends Throwable> reason) {
    final Consumer<Throwable> errorHandler = mock(Consumer.class);
    pendingRequest.then(responseStream -> fail("Should not have performed request"), errorHandler);

    verify(errorHandler).accept(any(reason));
  }

  @SuppressWarnings("unchecked")
  private void assertNotDone(final PendingPeerRequest pendingRequest) {
    final Consumer<RequestManager.ResponseStream> onSuccess = mock(Consumer.class);
    final Consumer<Throwable> onError = mock(Consumer.class);
    pendingRequest.then(onSuccess, onError);

    verifyNoInteractions(onSuccess);
    verifyNoInteractions(onError);
  }

  // The pre-STATUS (incomplete) connection cache is bounded, so a peer that completes the devp2p
  // HELLO but never sends sil STATUS cannot accumulate unbounded connections outside --max-peers
  // accounting.
  @Test
  public void incompleteConnectionsAreBounded() {
    final int limit = silPeers.getMaxIncompleteConnections();
    for (int i = 0; i < limit + 10; i++) {
      silPeers.registerNewConnection(mockIncompleteConnection(i), emptyList());
    }
    assertThat(silPeers.incompleteConnectionCount()).isLessThanOrEqualTo(limit);
    assertThat(silPeers.incompleteConnectionCount()).isPositive();
  }

  // An evicted connection that never completed sil STATUS must be disconnected so its socket / file
  // descriptor is released rather than leaked (the previous removal listener left a lone pre-STATUS
  // connection open on eviction).
  @Test
  public void evictedPreStatusConnectionIsDisconnected() {
    final PeerConnection connection = mock(PeerConnection.class);
    when(connection.isDisconnected()).thenReturn(false);
    final SilPeer peer = mock(SilPeer.class);
    when(peer.getConnection()).thenReturn(connection);
    when(peer.statusHasBeenReceived()).thenReturn(false);

    silPeers.onCacheRemoval(RemovalNotification.create(connection, peer, RemovalCause.SIZE));

    verify(connection).disconnect(DisconnectReason.TIMEOUT);
  }

  // A connection that completed sil STATUS and is being promoted to an active connection must NOT
  // be
  // disconnected when its incomplete-cache entry expires.
  @Test
  public void evictedPromotedConnectionIsNotDisconnected() {
    final PeerConnection connection = mock(PeerConnection.class);
    when(connection.isDisconnected()).thenReturn(false);
    final SilPeer peer = mock(SilPeer.class);
    when(peer.getConnection()).thenReturn(connection);
    when(peer.statusHasBeenReceived()).thenReturn(true);

    silPeers.onCacheRemoval(RemovalNotification.create(connection, peer, RemovalCause.SIZE));

    verify(connection, never()).disconnect(any());
  }

  // Explicit cache invalidation (e.g. a normal disconnect path) must not trigger a second
  // disconnect from the removal listener.
  @Test
  public void explicitCacheInvalidationDoesNotDisconnect() {
    final PeerConnection connection = mock(PeerConnection.class);
    when(connection.isDisconnected()).thenReturn(false);
    final SilPeer peer = mock(SilPeer.class);
    when(peer.getConnection()).thenReturn(connection);

    silPeers.onCacheRemoval(RemovalNotification.create(connection, peer, RemovalCause.EXPLICIT));

    verify(connection, never()).disconnect(any());
  }

  private PeerConnection mockIncompleteConnection(final int index) {
    final byte[] idBytes = new byte[64];
    idBytes[0] = (byte) (index >> 8);
    idBytes[1] = (byte) index;
    final Peer remotePeer = mock(Peer.class);
    when(remotePeer.getId()).thenReturn(Bytes.wrap(idBytes));
    final PeerConnection connection = mock(PeerConnection.class);
    when(connection.getPeer()).thenReturn(remotePeer);
    when(connection.isDisconnected()).thenReturn(false);
    return connection;
  }
}
