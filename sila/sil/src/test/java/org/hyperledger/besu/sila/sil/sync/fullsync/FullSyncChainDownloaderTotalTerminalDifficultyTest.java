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
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.sync.ChainDownloader;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class FullSyncChainDownloaderTotalTerminalDifficultyTest {

  protected ProtocolSchedule protocolSchedule;
  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;
  protected ProtocolContext protocolContext;
  private SyncState syncState;

  private BlockchainSetupUtil localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private PeerTaskExecutor peerTaskExecutor;
  private static final Difficulty TARGET_TERMINAL_DIFFICULTY = Difficulty.of(1_000_000L);

  static class FullSyncChainDownloaderTotalTerminalDifficultyTestArguments
      implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setupTest(final DataStorageFormat storageFormat) {
    localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);

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
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    silContext = silProtocolManager.silContext();
    syncState = new SyncState(protocolContext.getBlockchain(), silContext.getSilPeers());

    GetHeadersFromPeerTaskExecutorAnswer headersAnswer =
        new GetHeadersFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers());
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(headersAnswer);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(headersAnswer);

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetBodiesFromPeerTask.class)))
        .thenAnswer(
            new GetBodiesFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers()));
  }

  @AfterEach
  public void tearDown() {
    if (silProtocolManager != null) {
      silProtocolManager.stop();
    }
  }

  private ChainDownloader downloader(
      final SynchronizerConfiguration syncConfig,
      final SyncTerminationCondition terminalCondition) {
    return FullSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        silContext,
        syncState,
        metricsSystem,
        terminalCondition,
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS,
        peerTaskExecutor);
  }

  private SynchronizerConfiguration.Builder syncConfigBuilder() {
    return SynchronizerConfiguration.builder();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTotalTerminalDifficultyTestArguments.class)
  public void syncsFullyAndStopsWhenTTDReached(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(30);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(1).downloaderParallelism(1).build();
    final ChainDownloader downloader =
        downloader(
            syncConfig,
            SyncTerminationCondition.difficulty(TARGET_TERMINAL_DIFFICULTY, localBlockchain));
    final CompletableFuture<Void> future = downloader.start();

    assertThat(future.isDone()).isFalse();

    peer.respondWhileOtherThreadsWork(responder, () -> syncState.syncTarget().isEmpty());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    peer.respondWhileOtherThreadsWork(responder, () -> !future.isDone());

    assertThat(localBlockchain.getChainHead().getTotalDifficulty())
        .isGreaterThan(TARGET_TERMINAL_DIFFICULTY);

    assertThat(future.isDone()).isTrue();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTotalTerminalDifficultyTestArguments.class)
  public void syncsFullyAndContinuesWhenTTDNotSpecified(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(30);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(1).downloaderParallelism(1).build();
    final ChainDownloader downloader = downloader(syncConfig, SyncTerminationCondition.never());
    final CompletableFuture<Void> future = downloader.start();

    assertThat(future.isDone()).isFalse();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);

    assertThat(future.isDone()).isFalse();
  }
}
