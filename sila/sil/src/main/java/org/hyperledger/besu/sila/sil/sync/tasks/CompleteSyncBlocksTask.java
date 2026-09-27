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
package org.hyperledger.besu.sila.sil.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.SyncBlock;
import org.hyperledger.besu.sila.core.SyncBlockBody;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.sila.sil.manager.task.GetSyncBlocksFromPeerTask;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a set of headers, "completes" them by repeatedly requesting additional data (bodies) needed
 * to create the blocks that correspond to the supplied headers.
 */
public class CompleteSyncBlocksTask extends AbstractCompleteBlocksTask<SyncBlock> {
  private static final Logger LOG = LoggerFactory.getLogger(CompleteSyncBlocksTask.class);

  private CompleteSyncBlocksTask(
      final ProtocolSchedule protocolSchedule,
      final SilContext silContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, silContext, headers, maxRetries, metricsSystem);
    checkArgument(!headers.isEmpty(), "Must supply a non-empty headers list");
  }

  public static CompleteSyncBlocksTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final SilContext silContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new CompleteSyncBlocksTask(
        protocolSchedule, silContext, headers, DEFAULT_RETRIES, metricsSystem);
  }

  @Override
  long getBlockNumber(final SyncBlock syncBlock) {
    return syncBlock.getHeader().getNumber();
  }

  @Override
  SyncBlock createEmptyBlock(final BlockHeader header) {
    if (isWithdrawalsEnabled(header)) {
      return new SyncBlock(header, SyncBlockBody.emptyWithEmptyWithdrawals(protocolSchedule));
    } else {
      return new SyncBlock(header, SyncBlockBody.emptyWithNullWithdrawals(protocolSchedule));
    }
  }

  @Override
  CompletableFuture<List<SyncBlock>> requestBodies(final Optional<SilPeer> assignedPeer) {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    if (incompleteHeaders.isEmpty()) {
      return completedFuture(emptyList());
    }
    LOG.debug(
        "Requesting bodies to complete {} blocks, starting with {}.",
        incompleteHeaders.size(),
        incompleteHeaders.getFirst().getNumber());
    return executeSubTask(
        () -> {
          final GetSyncBlocksFromPeerTask task =
              GetSyncBlocksFromPeerTask.forHeaders(
                  silContext, incompleteHeaders, metricsSystem, protocolSchedule);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run().thenApply(PeerTaskResult::getResult);
        });
  }
}
