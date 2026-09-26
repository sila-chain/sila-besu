/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.sila.sil.sync.common;

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.PeerReputation;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PivotSelectorFromPeersTest {
  private @Mock SilContext silContext;
  private @Mock SilPeers silPeers;
  private @Mock SyncState syncState;

  private PivotSelectorFromPeers selector;

  @BeforeEach
  public void beforeTest() {
    SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().syncMinimumPeerCount(2).syncPivotDistance(1).build();

    selector = new PivotSelectorFromPeers(silContext, syncConfig, syncState, 120);
  }

  @Test
  public void testSelectNewPivotBlock() {
    SilPeerImmutableAttributes peer1 = SilPeerImmutableAttributes.from(mockPeer(true, 10, true));
    SilPeerImmutableAttributes peer2 = SilPeerImmutableAttributes.from(mockPeer(true, 8, true));

    Mockito.when(silContext.getEthPeers()).thenReturn(silPeers);
    Mockito.when(silPeers.streamAvailablePeers()).thenReturn(Stream.of(peer1, peer2));
    Mockito.when(silPeers.getBestPeerComparator())
        .thenReturn((p1, ignored) -> p1 == peer1 ? 1 : -1);

    try {
      SnapSyncProcessState result = selector.selectNewPivotBlock().get();
      Assertions.assertEquals(9, result.getPivotBlockNumber().getAsLong());
    } catch (Exception e) {
      Assertions.fail("Unexpected exception thrown", e);
    }
  }

  @Test
  public void reusesPreviousPivotWhileHeadIsWithinWindow() {
    // Cycle 1: best peer at height 10 → pivot = 10 - pivotDistance(1) = 9
    // Cycle 2: best peer at height 100 → 100 - 9 = 91 < 120, must reuse 9
    final SilPeerImmutableAttributes peer10a =
        SilPeerImmutableAttributes.from(mockPeer(true, 10, true));
    final SilPeerImmutableAttributes peer10b =
        SilPeerImmutableAttributes.from(mockPeer(true, 10, true));
    final SilPeerImmutableAttributes peer100 =
        SilPeerImmutableAttributes.from(mockPeer(true, 100, true));
    final SilPeerImmutableAttributes peer99 =
        SilPeerImmutableAttributes.from(mockPeer(true, 99, true));

    Mockito.when(silContext.getEthPeers()).thenReturn(silPeers);
    Mockito.when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(peer10a, peer10b))
        .thenReturn(Stream.of(peer100, peer99));
    Mockito.when(silPeers.getBestPeerComparator())
        .thenReturn(
            (left, right) ->
                Long.compare(left.estimatedChainHeight(), right.estimatedChainHeight()));

    try {
      Assertions.assertEquals(
          9, selector.selectNewPivotBlock().get().getPivotBlockNumber().getAsLong());
      Assertions.assertEquals(
          9, selector.selectNewPivotBlock().get().getPivotBlockNumber().getAsLong());
    } catch (Exception e) {
      Assertions.fail("Unexpected exception thrown", e);
    }
  }

  @Test
  public void rotatesPivotOnceHeadHasAdvancedBeyondWindow() {
    // Cycle 1: pivot = 9. Cycle 2: best peer at 200 → 200 - 9 = 191 >= 120 → rotate to 199.
    final SilPeerImmutableAttributes peer10a =
        SilPeerImmutableAttributes.from(mockPeer(true, 10, true));
    final SilPeerImmutableAttributes peer10b =
        SilPeerImmutableAttributes.from(mockPeer(true, 10, true));
    final SilPeerImmutableAttributes peer200 =
        SilPeerImmutableAttributes.from(mockPeer(true, 200, true));
    final SilPeerImmutableAttributes peer199 =
        SilPeerImmutableAttributes.from(mockPeer(true, 199, true));

    Mockito.when(silContext.getEthPeers()).thenReturn(silPeers);
    Mockito.when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(peer10a, peer10b))
        .thenReturn(Stream.of(peer200, peer199));
    Mockito.when(silPeers.getBestPeerComparator())
        .thenReturn(
            (left, right) ->
                Long.compare(left.estimatedChainHeight(), right.estimatedChainHeight()));

    try {
      Assertions.assertEquals(
          9, selector.selectNewPivotBlock().get().getPivotBlockNumber().getAsLong());
      Assertions.assertEquals(
          199, selector.selectNewPivotBlock().get().getPivotBlockNumber().getAsLong());
    } catch (Exception e) {
      Assertions.fail("Unexpected exception thrown", e);
    }
  }

  @Test
  public void testSelectNewPivotBlockWithInsufficientPeers() {
    Mockito.when(silContext.getEthPeers()).thenReturn(silPeers);
    Mockito.when(silPeers.streamAvailablePeers()).thenReturn(Stream.empty());

    try {
      selector.selectNewPivotBlock().get();
      Assertions.fail("Expected CompletableFuture to be failed but it completed successfully");
    } catch (Exception e) {
      // Expected - the future should fail when there are insufficient peers
    }
  }

  private SilPeer mockPeer(
      final boolean hasEstimatedHeight, final long chainHeight, final boolean isFullyValidated) {
    SilPeer silPeer = Mockito.mock(SilPeer.class);
    ChainState chainState = Mockito.mock(ChainState.class);
    Mockito.when(silPeer.chainState()).thenReturn(chainState);
    Mockito.when(chainState.hasEstimatedHeight()).thenReturn(hasEstimatedHeight);
    Mockito.when(chainState.getEstimatedHeight()).thenReturn(chainHeight);
    Mockito.when(silPeer.isFullyValidated()).thenReturn(isFullyValidated);
    Mockito.when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));
    Mockito.when(silPeer.getReputation()).thenReturn(new PeerReputation());
    PeerConnection connection = mock(PeerConnection.class);
    Mockito.when(silPeer.getConnection()).thenReturn(connection);

    return silPeer;
  }
}
