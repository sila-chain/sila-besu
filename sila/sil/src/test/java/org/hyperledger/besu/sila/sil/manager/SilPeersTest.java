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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Difficulty;
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
import java.util.function.Consumer;

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
    silPeers = silProtocolManager.silContext().getSilPeers();
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
                .getSilPeer());
    final SilPeerImmutableAttributes peerB =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(100), 10)
                .getSilPeer());

    assertThat(SilPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isLessThan(0);

    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerB)).isLessThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerA)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerA)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerB)).isEqualTo(0);

    assertThat(silProtocolManager.silContext().getSilPeers().bestPeer()).contains(peerB.silPeer());
    assertThat(silProtocolManager.silContext().getSilPeers().bestPeerWithHeightEstimate())
        .contains(peerB.silPeer());
  }

  @Test
  public void comparesPeersWithTdAndNoHeight() {
    final SilPeerImmutableAttributes peerA =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(
                    silProtocolManager, Difficulty.of(100), OptionalLong.empty())
                .getSilPeer());
    final SilPeerImmutableAttributes peerB =
        SilPeerImmutableAttributes.from(
            SilProtocolManagerTestUtil.createPeer(
                    silProtocolManager, Difficulty.of(50), OptionalLong.empty())
                .getSilPeer());

    // Sanity check
    assertThat(peerA.estimatedChainHeight()).isEqualTo(0);
    assertThat(peerB.estimatedChainHeight()).isEqualTo(0);

    assertThat(SilPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isGreaterThan(0);

    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerA)).isLessThan(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerA, peerA)).isEqualTo(0);
    assertThat(SilPeers.TOTAL_DIFFICULTY_THEN_HEIGHT.compare(peerB, peerB)).isEqualTo(0);

    assertThat(silProtocolManager.silContext().getSilPeers().bestPeer()).contains(peerA.silPeer());
    assertThat(silProtocolManager.silContext().getSilPeers().bestPeerWithHeightEstimate())
        .isEmpty();
  }

  @Test
  public void shouldExecutePeerRequestImmediatelyWhenPeerIsAvailable() throws Exception {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    when(peerRequest.isSilPeerSuitable(SilPeerImmutableAttributes.from(peer.getSilPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(peer.getSilPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastBusyPeerForRequest() throws Exception {
    final RespondingSilPeer idlePeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final RespondingSilPeer workingPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useRequestSlot(workingPeer.getSilPeer());

    when(peerRequest.isSilPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(idlePeer.getSilPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastRecentlyUsedPeerWhenBothHaveSameNumberOfOutstandingRequests()
      throws Exception {
    final RespondingSilPeer mostRecentlyUsedPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final RespondingSilPeer leastRecentlyUsedPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useRequestSlot(mostRecentlyUsedPeer.getSilPeer());
    freeUpCapacity(mostRecentlyUsedPeer.getSilPeer());

    assertThat(leastRecentlyUsedPeer.getSilPeer().outstandingRequests())
        .isEqualTo(mostRecentlyUsedPeer.getSilPeer().outstandingRequests());

    when(peerRequest.isSilPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(leastRecentlyUsedPeer.getSilPeer());
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
    final RespondingSilPeer suitablePeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(suitablePeer.getSilPeer());

    when(peerRequest.isSilPeerSuitable(SilPeerImmutableAttributes.from(suitablePeer.getSilPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 200, Optional.empty());

    verify(peerRequest, times(0)).sendRequest(suitablePeer.getSilPeer());

    assertNotDone(pendingRequest);

    suitablePeer.disconnect(DisconnectReason.TOO_MANY_PEERS);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWithPeerNotConnectedIfPeerRequestThrows() throws Exception {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    when(peerRequest.sendRequest(peer.getSilPeer())).thenThrow(new PeerNotConnected("Oh dear"));
    when(peerRequest.isSilPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());

    assertRequestFailure(pendingRequest, PeerDisconnectedException.class);
  }

  @Test
  public void shouldDelayExecutionUntilPeerHasCapacity() throws Exception {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(peer.getSilPeer());

    when(peerRequest.isSilPeerSuitable(any())).thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getSilPeer());

    freeUpCapacity(peer.getSilPeer());

    verify(peerRequest).sendRequest(peer.getSilPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldDelayExecutionUntilPeerWithSufficientHeightHasCapacity() throws Exception {
    // Create a peer that has available capacity but not the required height
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 10);

    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    when(peerRequest.isSilPeerSuitable(Mockito.any()))
        .thenAnswer(
            (invocationOnMock) -> {
              SilPeerImmutableAttributes silPeer =
                  invocationOnMock.getArgument(0, SilPeerImmutableAttributes.class);
              return silPeer.silPeer().equals(peer.getSilPeer());
            });
    useAllAvailableCapacity(peer.getSilPeer());

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getSilPeer());

    freeUpCapacity(peer.getSilPeer());

    verify(peerRequest).sendRequest(peer.getSilPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldNotExecuteAbortedRequest() throws Exception {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    useAllAvailableCapacity(peer.getSilPeer());

    when(peerRequest.isSilPeerSuitable(SilPeerImmutableAttributes.from(peer.getSilPeer())))
        .thenReturn(true);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verify(peerRequest, times(0)).sendRequest(peer.getSilPeer());

    pendingRequest.abort();

    freeUpCapacity(peer.getSilPeer());

    verify(peerRequest, times(0)).sendRequest(peer.getSilPeer());
    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  // We had a bug where if a peer was busy when it was disconnected, pending peer requests that were
  // *explicitly* assigned to that peer would never be attempted and thus never completed
  @Test
  public void shouldFailRequestWithBusyDisconnectedAssignedPeer() throws Exception {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = peer.getSilPeer();
    useAllAvailableCapacity(silPeer);

    final PendingPeerRequest pendingRequest =
        silPeers.executePeerRequest(peerRequest, 100, Optional.of(silPeer));

    silPeer.disconnect(DisconnectReason.UNKNOWN);
    silPeers.registerDisconnect(silPeer.getConnection());

    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  @Test
  public void shouldNotFailWhenAttemptExecutionDisconnectSamePeer() throws PeerNotConnected {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = spy(peer.getSilPeer());

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
    assertThat(peer.getSilPeer().isDisconnected()).isTrue(); // peer is disconnected
  }

  @Test
  public void shouldNotFailWhenAttemptExecutionDisconnectAnotherPeer() throws PeerNotConnected {
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    final SilPeer silPeer = spy(peer.getSilPeer());

    // Force request to be added to pending request list
    when(silPeer.hasAvailableRequestCapacity()).thenReturn(false);

    final PendingPeerRequest pendingPeerRequest =
        silPeers.executePeerRequest(peerRequest, 10, Optional.of(silPeer));

    final RespondingSilPeer peerToDisconnect =
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
    assertThat(peerToDisconnect.getSilPeer().isDisconnected()).isTrue(); // peer is disconnected
  }

  @Test
  public void toString_hasExpectedInfo() {
    assertThat(silPeers.toString()).isEqualTo("0 SilPeers {}");

    final SilPeer peerA =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(50), 20)
            .getSilPeer();
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
              .getSilPeer();
      assertThat(silPeers.addPeerToSilPeers(silPeer)).isTrue();
    }

    final SilPeer nonSnapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, false, false)
            .getSilPeer();

    assertThat(silPeers.addPeerToSilPeers(nonSnapServingPeer)).isFalse();
    assertThat(nonSnapServingPeer.getConnection().isDisconnected()).isTrue();

    final SilPeer snapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, true, false)
            .getSilPeer();

    assertThat(silPeers.addPeerToSilPeers(snapServingPeer)).isTrue();
    assertThat(silPeers.peerCount()).isEqualTo(silPeers.getMaxPeers());
  }

  @Test
  public void snapServersNotPreferredWhenInSync() {

    silPeers.snapServerPeersNeeded(false);

    while (silPeers.peerCount() < silPeers.getMaxPeers()) {
      final SilPeer silPeer =
          SilProtocolManagerTestUtil.createPeer(
                  silProtocolManager, Difficulty.of(50), 20, false, false)
              .getSilPeer();
      assertThat(silPeers.addPeerToSilPeers(silPeer)).isTrue();
    }

    final SilPeer snapServingPeer =
        SilProtocolManagerTestUtil.createPeer(
                silProtocolManager, Difficulty.of(50), 20, true, false)
            .getSilPeer();

    assertThat(silPeers.addPeerToSilPeers(snapServingPeer)).isFalse();
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
}
