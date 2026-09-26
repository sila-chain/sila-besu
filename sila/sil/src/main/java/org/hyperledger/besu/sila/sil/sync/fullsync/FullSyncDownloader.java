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

import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.sync.ChainDownloader;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.TrailingPeerRequirements;
import org.hyperledger.besu.sila.sil.sync.fullsync.era1prepipeline.Era1ImportPrepipelineFactory;
import org.hyperledger.besu.sila.sil.sync.fullsync.era1prepipeline.FileImportChainDownloader;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullSyncDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(FullSyncDownloader.class);
  private final ChainDownloader chainDownloader;
  private final Optional<ChainDownloader> era1PrepipelineChainDownloader;
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolContext protocolContext;
  private final SyncState syncState;

  public FullSyncDownloader(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final SyncTerminationCondition terminationCondition,
      final PeerTaskExecutor peerTaskExecutor,
      final SyncDurationMetrics syncDurationMetrics) {
    this.syncConfig = syncConfig;
    this.protocolContext = protocolContext;
    this.syncState = syncState;

    if (syncConfig.getSyncMode() == SyncMode.FULL && syncConfig.era1ImportPrepipelineEnabled()) {
      this.era1PrepipelineChainDownloader =
          Optional.of(
              new FileImportChainDownloader(
                  new Era1ImportPrepipelineFactory(
                      metricsSystem,
                      syncConfig.era1DataUri(),
                      syncConfig.era1ImportPrepipelineConcurrency(),
                      protocolSchedule,
                      protocolContext,
                      silContext,
                      terminationCondition),
                  protocolContext.getBlockchain(),
                  silContext.getScheduler()));
    } else {
      this.era1PrepipelineChainDownloader = Optional.empty();
    }

    this.chainDownloader =
        FullSyncChainDownloader.create(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silContext,
            syncState,
            metricsSystem,
            terminationCondition,
            syncDurationMetrics,
            peerTaskExecutor);
  }

  public CompletableFuture<Void> start() {
    if (era1PrepipelineChainDownloader.isPresent()) {
      LOG.info(
          "Starting ERA1 file import prepipeline. Full sync will start after prepipeline completion");
      CompletableFuture<Void> era1PipelineFuture = era1PrepipelineChainDownloader.get().start();
      return era1PipelineFuture.thenAccept(
          (v) -> {
            LOG.info("Starting full sync.");
            chainDownloader.start();
          });

    } else {
      LOG.info("Starting full sync.");
      return chainDownloader.start();
    }
  }

  public void stop() {
    era1PrepipelineChainDownloader.ifPresent((p) -> p.cancel());
    chainDownloader.cancel();
  }

  public TrailingPeerRequirements calculateTrailingPeerRequirements() {
    return syncState.isInSync()
        ? TrailingPeerRequirements.UNRESTRICTED
        : new TrailingPeerRequirements(
            protocolContext.getBlockchain().getChainHeadBlockNumber(),
            syncConfig.getMaxTrailingPeers());
  }
}
