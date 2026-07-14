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
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.sync.ChainDownloader;
import org.hyperledger.besu.sila.sil.sync.PipelineChainDownloader;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class FullSyncChainDownloader {
  private FullSyncChainDownloader() {}

  public static ChainDownloader create(
      final SynchronizerConfiguration config,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final SyncTerminationCondition terminationCondition,
      final SyncDurationMetrics syncDurationMetrics,
      final PeerTaskExecutor peerTaskExecutor) {

    final FullSyncTargetManager syncTargetManager =
        new FullSyncTargetManager(
            config,
            protocolSchedule,
            protocolContext,
            silContext,
            metricsSystem,
            terminationCondition);

    return new PipelineChainDownloader(
        syncState,
        syncTargetManager,
        new FullSyncDownloadPipelineFactory(
            config,
            protocolSchedule,
            protocolContext,
            silContext,
            metricsSystem,
            terminationCondition),
        silContext.getScheduler(),
        metricsSystem,
        syncDurationMetrics);
  }
}
