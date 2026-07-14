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

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.sync.ChainDownloader;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class FullSyncChainDownloaderTest {

  protected ProtocolSchedule protocolSchedule;
  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;
  protected ProtocolContext protocolContext;
  private SyncState syncState;

  private BlockDataGenerator gen;
  private BlockchainSetupUtil localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private PeerTaskExecutor peerTaskExecutor;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  static class FullSyncChainDownloaderTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setupTest(final DataStorageFormat storageFormat) {
    gen = new BlockDataGenerator();
    localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
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

    GetHeadersFromPeerTaskExecutorAnswer getHeadersAnswer =
        new GetHeadersFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers());
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(getHeadersAnswer);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(getHeadersAnswer);

    GetBodiesFromPeerTaskExecutorAnswer getBodiesAnswer =
        new GetBodiesFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers());
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetBodiesFromPeerTask.class)))
        .thenAnswer(getBodiesAnswer);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetBodiesFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(getBodiesAnswer);
  }

  @AfterEach
  public void tearDown() {
    if (silProtocolManager != null) {
      silProtocolManager.stop();
    }
  }

  private ChainDownloader downloader(final SynchronizerConfiguration syncConfig) {
    return FullSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        silContext,
        syncState,
        metricsSystem,
        SyncTerminationCondition.never(),
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS,
        peerTaskExecutor);
  }

  private ChainDownloader downloader() {
    final SynchronizerConfiguration syncConfig = syncConfigBuilder().build();
    return downloader(syncConfig);
  }

  private SynchronizerConfiguration.Builder syncConfigBuilder() {
    return SynchronizerConfiguration.builder();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_multipleSegments(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_singleSegment(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_singleSegmentOnBoundary(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(5);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();
    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(4).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(responder, () -> !syncState.syncTarget().isPresent());
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    peer.respondWhileOtherThreadsWork(
        responder, () -> localBlockchain.getChainHeadBlockNumber() < targetBlock);

    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void doesNotSyncToWorseChain(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    localBlockchainSetup.importFirstBlocks(15);
    // Sanity check
    assertThat(localBlockchain.getChainHeadBlockNumber())
        .isGreaterThan(BlockHeader.GENESIS_BLOCK_NUMBER);

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);

    final ChainDownloader downloader = downloader();
    downloader.start();

    assertThat(syncState.syncTarget()).isNotPresent();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void syncsToBetterChain_fromFork(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    otherBlockchainSetup.importFirstBlocks(15);
    final long targetBlock = otherBlockchain.getChainHeadBlockNumber();

    // Add divergent blocks to local chain
    localBlockchainSetup.importFirstBlocks(3);
    gen = new BlockDataGenerator();
    final Block chainHead = localBlockchain.getChainHeadBlock();
    final Block forkBlock =
        gen.block(gen.nextBlockOptions(chainHead).setDifficulty(Difficulty.ZERO));
    localBlockchain.appendBlock(forkBlock, gen.receipts(forkBlock));

    // Sanity check
    assertThat(targetBlock).isGreaterThan(localBlockchain.getChainHeadBlockNumber());
    assertThat(otherBlockchain.contains(localBlockchain.getChainHead().getHash())).isFalse();

    final RespondingSilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, otherBlockchain);
    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);

    final SynchronizerConfiguration syncConfig =
        syncConfigBuilder().downloaderChainSegmentSize(10).build();
    final ChainDownloader downloader = downloader(syncConfig);
    downloader.start();

    peer.respondWhileOtherThreadsWork(
        responder,
        () ->
            localBlockchain.getChainHeadBlockNumber() < targetBlock
                || syncState.syncTarget().isPresent());

    // Synctarget should not exist as chain has fully downloaded.
    assertThat(syncState.syncTarget().isPresent()).isFalse();
    assertThat(localBlockchain.getChainHeadBlockNumber()).isEqualTo(targetBlock);
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void choosesBestPeerAsSyncTarget_byTd(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    final Difficulty localTd = localBlockchain.getChainHead().getTotalDifficulty();

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, localTd.add(100));
    final RespondingSilPeer peerB =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, localTd.add(200));

    final ChainDownloader downloader = downloader();
    downloader.start();

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getSilPeer());
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncChainDownloaderTestArguments.class)
  public void choosesBestPeerAsSyncTarget_byTdAndHeight(final DataStorageFormat storageFormat) {
    setupTest(storageFormat);
    final Difficulty localTd = localBlockchain.getChainHead().getTotalDifficulty();

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, localTd.add(100), 100);
    final RespondingSilPeer peerB =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, localTd.add(200), 50);

    final ChainDownloader downloader = downloader();
    downloader.start();

    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peerB.getSilPeer());
  }
}
