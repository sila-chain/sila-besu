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

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.services.pipeline.PipelineBuilder;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.sync.DownloadBodiesStep;
import org.hyperledger.besu.sila.sil.sync.DownloadHeadersStep;
import org.hyperledger.besu.sila.sil.sync.DownloadPipelineFactory;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.ValidationPolicy;
import org.hyperledger.besu.sila.sil.sync.range.RangeHeadersFetcher;
import org.hyperledger.besu.sila.sil.sync.range.RangeHeadersValidationStep;
import org.hyperledger.besu.sila.sil.sync.range.SyncTargetRangeSource;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.sync.state.SyncTarget;
import org.hyperledger.besu.sila.silaMainnet.HeaderValidationMode;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullSyncDownloadPipelineFactory implements DownloadPipelineFactory {
  private static final Logger LOG = LoggerFactory.getLogger(FullSyncDownloadPipelineFactory.class);

  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final SilContext silContext;
  private final MetricsSystem metricsSystem;
  private final ValidationPolicy detachedValidationPolicy =
      () -> HeaderValidationMode.DETACHED_ONLY;
  private final BetterSyncTargetEvaluator betterSyncTargetEvaluator;
  private final SyncTerminationCondition fullSyncTerminationCondition;

  public FullSyncDownloadPipelineFactory(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final MetricsSystem metricsSystem,
      final SyncTerminationCondition syncTerminationCondition) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.silContext = silContext;
    this.metricsSystem = metricsSystem;
    this.fullSyncTerminationCondition = syncTerminationCondition;
    this.betterSyncTargetEvaluator =
        new BetterSyncTargetEvaluator(syncConfig, silContext.getSilPeers());
  }

  @Override
  public CompletionStage<Void> startPipeline(
      final SilScheduler scheduler,
      final SyncState syncState,
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline) {
    return scheduler.startPipeline(pipeline);
  }

  @Override
  public Pipeline<?> createDownloadPipelineForSyncTarget(
      final SyncState syncState, final SyncTarget target) {
    final int downloaderParallelism = syncConfig.getDownloaderParallelism();
    final int headerRequestSize = syncConfig.getDownloaderHeaderRequestSize();
    final int singleHeaderBufferSize = headerRequestSize * downloaderParallelism;
    final SyncTargetRangeSource checkpointRangeSource =
        new SyncTargetRangeSource(
            new RangeHeadersFetcher(syncConfig, protocolSchedule, silContext),
            this::shouldContinueDownloadingFromPeer,
            silContext.getScheduler(),
            target.peer(),
            target.commonAncestor(),
            syncConfig.getDownloaderCheckpointRetries(),
            fullSyncTerminationCondition);
    final DownloadHeadersStep downloadHeadersStep =
        new DownloadHeadersStep(
            protocolSchedule,
            protocolContext,
            silContext,
            detachedValidationPolicy,
            headerRequestSize,
            metricsSystem);
    final RangeHeadersValidationStep validateHeadersJoinUpStep = new RangeHeadersValidationStep();
    final DownloadBodiesStep downloadBodiesStep =
        new DownloadBodiesStep(protocolSchedule, silContext);
    final ExtractTxSignaturesStep extractTxSignaturesStep = new ExtractTxSignaturesStep();
    final FullImportBlockStep importBlockStep =
        new FullImportBlockStep(
            protocolSchedule, protocolContext, silContext, fullSyncTerminationCondition);

    return PipelineBuilder.createPipelineFrom(
            "fetchCheckpoints",
            checkpointRangeSource,
            downloaderParallelism,
            metricsSystem.createLabelledCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "chain_download_pipeline_processed_total",
                "Number of entries process by each chain download pipeline stage",
                "step",
                "action"),
            true,
            "fullSync")
        .thenProcessAsyncOrdered("downloadHeaders", downloadHeadersStep, downloaderParallelism)
        .thenFlatMap("validateHeadersJoin", validateHeadersJoinUpStep, singleHeaderBufferSize)
        .inBatches(headerRequestSize)
        .thenProcessAsyncOrdered("downloadBodies", downloadBodiesStep, downloaderParallelism)
        .thenFlatMap("extractTxSignatures", extractTxSignaturesStep, singleHeaderBufferSize)
        .andFinishWith("importBlock", importBlockStep);
  }

  private boolean shouldContinueDownloadingFromPeer(
      final SilPeer peer, final BlockHeader lastCheckpointHeader) {
    final boolean shouldTerminate = fullSyncTerminationCondition.shouldStopDownload();
    final boolean caughtUpToPeer =
        peer.chainState().getEstimatedHeight() <= lastCheckpointHeader.getNumber();
    final boolean isDisconnected = peer.isDisconnected();
    final boolean shouldSwitchSyncTarget = betterSyncTargetEvaluator.shouldSwitchSyncTarget(peer);
    LOG.debug(
        "shouldTerminate {}, shouldContinueDownloadingFromPeer? {}, disconnected {}, caughtUp {}, shouldSwitchSyncTarget {}",
        shouldTerminate,
        peer,
        isDisconnected,
        caughtUpToPeer,
        shouldSwitchSyncTarget);

    return !shouldTerminate && !isDisconnected && !caughtUpToPeer && !shouldSwitchSyncTarget;
  }
}
