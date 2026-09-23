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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.TrailingPeerRequirements;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class FullSyncDownloaderTest {

  protected ProtocolSchedule protocolSchedule;
  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;
  protected ProtocolContext protocolContext;
  private SyncState syncState;

  private BlockchainSetupUtil localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  static class FullSyncDownloaderTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setupTest(final DataStorageFormat storageFormat) {
    localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(localBlockchain)
            .setSilScheduler(new SilScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
            .setWorldStateArchive(localBlockchainSetup.getWorldArchive())
            .setTransactionPool(localBlockchainSetup.getTransactionPool())
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build();
    silContext = silProtocolManager.silContext();
    syncState = new SyncState(protocolContext.getBlockchain(), silContext.getSilPeers());
  }

  @AfterEach
  public void tearDown() {
    if (silProtocolManager != null) {
      silProtocolManager.stop();
    }
  }

  private FullSyncDownloader downloader(final SynchronizerConfiguration syncConfig) {
    return new FullSyncDownloader(
        syncConfig,
        protocolSchedule,
        protocolContext,
        silContext,
        syncState,
        metricsSystem,
        SyncTerminationCondition.never(),
        null,
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncDownloaderTestArguments.class)
  public void shouldLimitTrailingPeersWhenBehindChain(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(2);
    final int maxTailingPeers = 5;
    final FullSyncDownloader synchronizer =
        downloader(SynchronizerConfiguration.builder().maxTrailingPeers(maxTailingPeers).build());

    final RespondingSilPeer bestPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 100);
    syncState.setSyncTarget(bestPeer.getSilPeer(), localBlockchain.getChainHeadHeader());

    final TrailingPeerRequirements expected =
        new TrailingPeerRequirements(localBlockchain.getChainHeadBlockNumber(), maxTailingPeers);
    assertThat(synchronizer.calculateTrailingPeerRequirements()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncDownloaderTestArguments.class)
  public void shouldNotLimitTrailingPeersWhenInSync(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(2);
    final int maxTailingPeers = 5;
    final FullSyncDownloader synchronizer =
        downloader(SynchronizerConfiguration.builder().maxTrailingPeers(maxTailingPeers).build());

    final RespondingSilPeer bestPeer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 2);
    syncState.setSyncTarget(bestPeer.getSilPeer(), localBlockchain.getChainHeadHeader());

    assertThat(synchronizer.calculateTrailingPeerRequirements())
        .isEqualTo(TrailingPeerRequirements.UNRESTRICTED);
  }
}
