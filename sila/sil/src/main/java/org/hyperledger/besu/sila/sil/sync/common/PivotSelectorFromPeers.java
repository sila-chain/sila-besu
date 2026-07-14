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
package org.hyperledger.besu.sila.sil.sync.common;

import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.sync.PivotBlockSelector;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.TrailingPeerLimiter;
import org.hyperledger.besu.sila.sil.sync.TrailingPeerRequirements;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromPeers implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromPeers.class);

  protected final SilContext silContext;
  protected final SynchronizerConfiguration syncConfig;
  private final SyncState syncState;
  private final int pivotBlockWindowValidity;

  private volatile long lastReturnedPivotNumber = -1;

  public PivotSelectorFromPeers(
      final SilContext silContext,
      final SynchronizerConfiguration syncConfig,
      final SyncState syncState,
      final int pivotBlockWindowValidity) {
    this.silContext = silContext;
    this.syncConfig = syncConfig;
    this.syncState = syncState;
    this.pivotBlockWindowValidity = pivotBlockWindowValidity;
  }

  @Override
  public CompletableFuture<SnapSyncProcessState> selectNewPivotBlock() {
    return selectBestPeer()
        .map(this::fromPeer)
        .orElse(
            CompletableFuture.failedFuture(
                new RuntimeException("Not enough peers to select pivot block")));
  }

  @Override
  public CompletableFuture<Void> prepareRetry() {
    final long estimatedPivotBlock = conservativelyEstimatedPivotBlock();
    final TrailingPeerLimiter trailingPeerLimiter =
        new TrailingPeerLimiter(
            silContext.getSilPeers(),
            () ->
                new TrailingPeerRequirements(
                    estimatedPivotBlock, syncConfig.getMaxTrailingPeers()));
    trailingPeerLimiter.enforceTrailingPeerLimit();

    return silContext
        .getSilPeers()
        .waitForPeer((peer) -> peer.estimatedChainHeight() >= estimatedPivotBlock)
        .thenRun(() -> {});
  }

  @Override
  public long getBestChainHeight() {
    return syncState.bestChainHeight();
  }

  protected CompletableFuture<SnapSyncProcessState> fromPeer(final SilPeer peer) {
    final long bestPeerHeight = peer.chainState().getEstimatedHeight();

    // Reuse the previously selected pivot while the best peer's head is still within the
    // snap-serving window — avoids rotating the pivot on every check.
    if (lastReturnedPivotNumber > 0
        && bestPeerHeight - lastReturnedPivotNumber < pivotBlockWindowValidity) {
      LOG.debug(
          "Reusing pivot {} — best peer height {} within {} block window",
          lastReturnedPivotNumber,
          bestPeerHeight,
          pivotBlockWindowValidity);
      return CompletableFuture.completedFuture(
          new SnapSyncProcessState(lastReturnedPivotNumber, false));
    }

    final long pivotBlockNumber = bestPeerHeight - syncConfig.getSyncPivotDistance();
    if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Peer's chain isn't long enough, return an empty value, so we can try again.
      LOG.info("Waiting for peers with sufficient chain height");
      return CompletableFuture.failedFuture(
          new RuntimeException("No peers with sufficient height"));
    }
    lastReturnedPivotNumber = pivotBlockNumber;
    LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
    return CompletableFuture.completedFuture(new SnapSyncProcessState(pivotBlockNumber, false));
  }

  protected Optional<SilPeer> selectBestPeer() {
    List<SilPeerImmutableAttributes> peers =
        silContext
            .getSilPeers()
            .streamAvailablePeers()
            .filter((peer) -> peer.hasEstimatedChainHeight() && peer.isFullyValidated())
            .toList();

    // Only select a pivot block number when we have a minimum number of height estimates
    final int minPeerCount = syncConfig.getSyncMinimumPeerCount();
    if (peers.size() < minPeerCount) {
      LOG.info(
          "Waiting for valid peers with chain height information.  {} / {} required peers currently available.",
          peers.size(),
          minPeerCount);
      return Optional.empty();
    } else {
      return peers.stream()
          .max(silContext.getSilPeers().getBestPeerComparator())
          .map(SilPeerImmutableAttributes::silPeer);
    }
  }

  private long conservativelyEstimatedPivotBlock() {
    final long estimatedNextPivot =
        syncState.getLocalChainHeight() + syncConfig.getSyncPivotDistance();
    return Math.min(syncState.bestChainHeight(), estimatedNextPivot);
  }
}
