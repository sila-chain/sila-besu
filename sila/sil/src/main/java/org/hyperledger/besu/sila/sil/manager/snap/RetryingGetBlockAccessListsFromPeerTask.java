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
package org.hyperledger.besu.sila.sil.manager.snap;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.SyncBlockAccessList;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.sila.sil.manager.task.AbstractRetryingSwitchingPeerTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetBlockAccessListsFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<List<SyncBlockAccessList>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(RetryingGetBlockAccessListsFromPeerTask.class);

  public static final int MAX_RETRIES = 16;

  private final SilContext silContext;
  private final List<BlockHeader> blockHeaders;
  private final MetricsSystem metricsSystem;
  private final List<SyncBlockAccessList> blockAccessLists;
  private final Set<Integer> pendingIndexes = new LinkedHashSet<>();

  public RetryingGetBlockAccessListsFromPeerTask(
      final SilContext silContext,
      final List<BlockHeader> blockHeaders,
      final MetricsSystem metricsSystem) {
    super(silContext, metricsSystem, List::isEmpty, MAX_RETRIES);
    this.silContext = silContext;
    this.blockHeaders = blockHeaders;
    this.metricsSystem = metricsSystem;
    this.blockAccessLists = new ArrayList<>(Collections.nCopies(blockHeaders.size(), null));
    for (int i = 0; i < blockHeaders.size(); i++) {
      pendingIndexes.add(i);
    }
  }

  @Override
  protected CompletableFuture<List<SyncBlockAccessList>> executeTaskOnCurrentPeer(
      final SilPeer peer) {
    if (pendingIndexes.isEmpty()) {
      return CompletableFuture.completedFuture(completedBlockAccessLists());
    }

    final List<Integer> requestedIndexes = List.copyOf(pendingIndexes);
    LOG.atDebug()
        .setMessage(
            "Requesting {} block access list(s) from peer {} (attempt {}/{}, {} of {} still pending)")
        .addArgument(requestedIndexes::size)
        .addArgument(peer::getLoggableId)
        .addArgument(() -> getRetryCount() + 1)
        .addArgument(this::getMaxRetries)
        .addArgument(pendingIndexes::size)
        .addArgument(blockHeaders::size)
        .log();
    return requestBlockAccessListsFromPeer(peer, requestedIndexes)
        .thenApply(
            receivedBlockAccessLists -> {
              final int pendingCountBeforeProcessing = pendingIndexes.size();
              processBlockAccessLists(requestedIndexes, receivedBlockAccessLists);
              if (pendingIndexes.isEmpty()) {
                LOG.atDebug()
                    .setMessage("All {} block access list(s) fetched from peers")
                    .addArgument(blockHeaders::size)
                    .log();
                return completedBlockAccessLists();
              }
              if (pendingIndexes.size() < pendingCountBeforeProcessing) {
                LOG.atDebug()
                    .setMessage(
                        "Block access list partial progress: {}/{} fetched, {} still pending, retrying")
                    .addArgument(() -> blockHeaders.size() - pendingIndexes.size())
                    .addArgument(blockHeaders::size)
                    .addArgument(pendingIndexes::size)
                    .log();
                resetRetryCount();
              }
              throw new IncompleteResultsException(
                  "Downloaded "
                      + (blockHeaders.size() - pendingIndexes.size())
                      + " of "
                      + blockHeaders.size()
                      + " block access lists");
            });
  }

  protected CompletableFuture<List<SyncBlockAccessList>> requestBlockAccessListsFromPeer(
      final SilPeer peer, final List<Integer> requestedIndexes) {
    final GetBlockAccessListsFromPeerTask task =
        new GetBlockAccessListsFromPeerTask(
            silContext, requestedIndexes.stream().map(blockHeaders::get).toList(), metricsSystem);
    task.assignPeer(peer);
    return executeSubTask(task::run).thenApply(peerResult -> peerResult.getResult());
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return super.isRetryableError(error) || error instanceof IncompleteResultsException;
  }

  @VisibleForTesting
  void processBlockAccessLists(
      final List<Integer> requestedIndexes,
      final List<SyncBlockAccessList> receivedBlockAccessLists) {
    for (int i = 0; i < receivedBlockAccessLists.size(); i++) {
      final SyncBlockAccessList blockAccessList = receivedBlockAccessLists.get(i);
      if (blockAccessList.isUnavailable()) {
        continue;
      }

      final int originalIndex = requestedIndexes.get(i);
      if (pendingIndexes.remove(originalIndex)) {
        blockAccessLists.set(originalIndex, blockAccessList);
      }
    }
  }

  private List<SyncBlockAccessList> completedBlockAccessLists() {
    return List.copyOf(blockAccessLists);
  }

  @VisibleForTesting
  int pendingBlockAccessLists() {
    return pendingIndexes.size();
  }

  @Override
  protected boolean isSuitablePeer(final SilPeerImmutableAttributes peer) {
    return peer.isServingSnap()
        && peer.silPeer().getAgreedCapabilities().contains(SnapProtocol.SNAP2);
  }
}
