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

import static com.google.common.base.Preconditions.checkArgument;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.ChainHead;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidator;
import org.hyperledger.besu.sila.sil.sync.ChainHeadTracker;
import org.hyperledger.besu.sila.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import org.mockito.Mockito;

public class SilProtocolManagerTestUtil {

  public static ChainHeadTracker getChainHeadTrackerMock() {
    final ChainHeadTracker chtMock = mock(ChainHeadTracker.class);
    final BlockHeader blockHeaderMock = mock(BlockHeader.class);
    Mockito.lenient()
        .when(chtMock.getBestHeaderFromPeer(any()))
        .thenReturn(CompletableFuture.completedFuture(blockHeaderMock));
    Mockito.lenient().when(blockHeaderMock.getNumber()).thenReturn(0L);
    Mockito.lenient().when(blockHeaderMock.getStateRoot()).thenReturn(Hash.ZERO);
    return chtMock;
  }

  // Utility to prevent scheduler from automatically running submitted tasks
  public static void disableSilSchedulerAutoRun(final SilProtocolManager silProtocolManager) {
    final SilScheduler scheduler = silProtocolManager.silContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicSilScheduler,
        "SilProtocolManager must be set up with "
            + DeterministicSilScheduler.class.getSimpleName()
            + " in order to disable auto run.");
    ((DeterministicSilScheduler) scheduler).disableAutoRun();
  }

  // Manually runs any pending tasks submitted to the SilScheduler
  // Works with {@code disableSilSchedulerAutoRun} - tasks will only be pending if
  // autoRun has been disabled.
  public static void runPendingFutures(final SilProtocolManager silProtocolManager) {
    final SilScheduler scheduler = silProtocolManager.silContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicSilScheduler,
        "SilProtocolManager must be set up with "
            + DeterministicSilScheduler.class.getSimpleName()
            + " in order to manually run pending futures.");
    ((DeterministicSilScheduler) scheduler).runPendingFutures();
  }

  /**
   * Expires any pending timeouts tracked by {@code DeterministicSilScheduler}
   *
   * @param silProtocolManager The {@code SilProtocolManager} managing the scheduler holding the
   *     timeouts to be expired.
   */
  public static void expirePendingTimeouts(final SilProtocolManager silProtocolManager) {
    final SilScheduler scheduler = silProtocolManager.silContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicSilScheduler,
        "SilProtocolManager must be set up with "
            + DeterministicSilScheduler.class.getSimpleName()
            + " in order to manually expire pending timeouts.");
    ((DeterministicSilScheduler) scheduler).expirePendingTimeouts();
  }

  /**
   * Gets the number of pending tasks submitted to the SilScheduler.
   *
   * <p>Works with {@code disableSilSchedulerAutoRun} - tasks will only be pending if autoRun has
   * been disabled.
   */
  public static long getPendingFuturesCount(final SilProtocolManager silProtocolManager) {
    final SilScheduler scheduler = silProtocolManager.silContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicSilScheduler,
        "SilProtocolManager must be set up with "
            + DeterministicSilScheduler.class.getSimpleName()
            + " in order to manually run pending futures.");
    return ((DeterministicSilScheduler) scheduler).getPendingFuturesCount();
  }

  public static void broadcastMessage(
      final SilProtocolManager silProtocolManager,
      final RespondingSilPeer peer,
      final MessageData message) {
    silProtocolManager.processMessage(
        SilProtocol.LATEST, new DefaultMessage(peer.getPeerConnection(), message));
  }

  public static RespondingSilPeer.Builder peerBuilder() {
    return RespondingSilPeer.builder();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager, final Difficulty td) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(td)
        .capability(SilProtocol.SIL68)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final Difficulty td,
      final long estimatedHeight) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(td)
        .estimatedHeight(estimatedHeight)
        .capability(SilProtocol.SIL68)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final Difficulty td,
      final OptionalLong estimatedHeight) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(td)
        .estimatedHeight(estimatedHeight)
        .capability(SilProtocol.SIL68)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final OptionalLong estimatedHeight,
      final PeerValidator... validators) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .estimatedHeight(estimatedHeight)
        .peerValidators(validators)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final Difficulty td,
      final OptionalLong estimatedHeight,
      final PeerValidator... validators) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(td)
        .estimatedHeight(estimatedHeight)
        .capability(SilProtocol.SIL68)
        .peerValidators(validators)
        .build();
  }

  public static RespondingSilPeer createPeer(final SilProtocolManager silProtocolManager) {
    return RespondingSilPeer.builder().silProtocolManager(silProtocolManager).build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager, final long estimatedHeight) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .estimatedHeight(estimatedHeight)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final SnapProtocolManager snapProtocolManager,
      final long estimatedHeight) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .estimatedHeight(estimatedHeight)
        .snapProtocolManager(snapProtocolManager)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final long estimatedHeight,
      final PeerValidator... validators) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .estimatedHeight(estimatedHeight)
        .peerValidators(validators)
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager, final Blockchain blockchain) {
    final ChainHead head = blockchain.getChainHead();
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(head.getTotalDifficulty())
        .chainHeadHash(head.getHash())
        .estimatedHeight(blockchain.getChainHeadBlockNumber())
        .build();
  }

  public static RespondingSilPeer createPeer(
      final SilProtocolManager silProtocolManager,
      final Difficulty td,
      final int estimatedHeight,
      final boolean isServingSnap,
      final boolean addToSilPeers) {
    return RespondingSilPeer.builder()
        .silProtocolManager(silProtocolManager)
        .totalDifficulty(td)
        .estimatedHeight(estimatedHeight)
        .isServingSnap(isServingSnap)
        .addToSilPeers(addToSilPeers)
        .build();
  }
}
