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
package org.hyperledger.besu.sila.sil.sync.fullsync;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockImporter;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.sila.silaMainnet.BlockImportResult;
import org.hyperledger.besu.sila.silaMainnet.HeaderValidationMode;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.time.Instant;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullImportBlockStep implements Consumer<Block> {
  private static final Logger LOG = LoggerFactory.getLogger(FullImportBlockStep.class);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final SilContext silContext;
  private long gasAccumulator = 0;
  private long lastReportMillis = 0;
  private final SyncTerminationCondition fullSyncTerminationCondition;

  public FullImportBlockStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final SyncTerminationCondition syncTerminationCondition) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.silContext = silContext;
    this.fullSyncTerminationCondition = syncTerminationCondition;
  }

  @Override
  public void accept(final Block block) {
    if (fullSyncTerminationCondition.shouldStopDownload()) {
      LOG.debug("Not importing another block, because terminal condition was reached.");
      return;
    }
    final long blockNumber = block.getHeader().getNumber();
    final String blockHash = block.getHash().getBytes().toHexString();
    final BlockImporter importer =
        protocolSchedule.getByBlockHeader(block.getHeader()).getBlockImporter();
    final BlockImportResult blockImportResult =
        importer.importBlock(protocolContext, block, HeaderValidationMode.SKIP_DETACHED);
    if (!blockImportResult.isImported()) {
      throw InvalidBlockException.fromInvalidBlock(block.getHeader());
    }
    gasAccumulator += block.getHeader().getGasUsed();
    int peerCount = -1; // silContext is not available in tests
    if (silContext != null && silContext.getSilPeers().peerCount() >= 0) {
      peerCount = silContext.getSilPeers().peerCount();
    }
    if (blockNumber % 200 == 0 || LOG.isTraceEnabled()) {
      final long nowMilli = Instant.now().toEpochMilli();
      final long deltaMilli = nowMilli - lastReportMillis;
      final String mgps =
          (lastReportMillis == 0 || gasAccumulator == 0)
              ? "-"
              : String.format("%.3f", gasAccumulator / 1000.0 / deltaMilli);
      LOG.info(
          "Import reached block {} ({}), {} Mg/s, Peers: {}",
          blockNumber,
          blockHash,
          mgps,
          peerCount);
      lastReportMillis = nowMilli;
      gasAccumulator = 0;
    }
  }
}
