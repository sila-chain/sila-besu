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
package org.hyperledger.besu.sila.sil.sync.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidator;
import org.hyperledger.besu.sila.sil.sync.PivotBlockSelector;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class PivotSyncActionsTest {
  private final WorldStateStorageCoordinator worldStateStorageCoordinator =
      mock(WorldStateStorageCoordinator.class);
  private final AtomicInteger timeoutCount = new AtomicInteger(0);
  private SynchronizerConfiguration syncConfig;
  private PivotSyncActions pivotSyncActions;
  private SilProtocolManager silProtocolManager;
  private SilContext silContext;
  private SilPeers silPeers;
  private MutableBlockchain blockchain;
  private BlockchainSetupUtil blockchainSetupUtil;
  private SyncState syncState;

  static class PivotSyncActionsTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.empty());
  }

  public void setUp(
      final DataStorageFormat storageFormat, final Optional<Integer> syncMinimumPeers) {
    SynchronizerConfiguration.Builder syncConfigBuilder =
        new SynchronizerConfiguration.Builder().syncMode(SyncMode.SNAP).syncPivotDistance(1000);
    syncMinimumPeers.ifPresent(syncConfigBuilder::syncMinimumPeerCount);
    syncConfig = syncConfigBuilder.build();
    when(worldStateStorageCoordinator.getDataStorageFormat()).thenReturn(storageFormat);
    blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(ProtocolScheduleFixture.TESTING_NETWORK)
            .setBlockchain(blockchain)
            .setSilScheduler(
                new DeterministicSilScheduler(() -> timeoutCount.getAndDecrement() > 0))
            .setWorldStateArchive(blockchainSetupUtil.getWorldArchive())
            .setTransactionPool(blockchainSetupUtil.getTransactionPool())
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build();
    silContext = silProtocolManager.silContext();
    silPeers = silContext.getSilPeers();
    syncState = new SyncState(blockchain, silPeers);
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void waitForPeersShouldSucceedIfEnoughPeersAreFound(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    for (int i = 0; i < syncConfig.getSyncMinimumPeerCount(); i++) {
      SilProtocolManagerTestUtil.createPeer(
          silProtocolManager, syncConfig.getSyncPivotDistance() + i + 1);
    }
    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    assertThat(result).isCompletedWithValue(new SnapSyncProcessState(5, false));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void returnTheSamePivotBlockIfAlreadySelected(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final SnapSyncProcessState fastSyncState = new SnapSyncProcessState(pivotHeader, false);
    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(fastSyncState);
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(fastSyncState);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldUseExistingPivotBlockIfAvailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 5000);

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState(pivotHeader, false));
    final SnapSyncProcessState expected = new SnapSyncProcessState(pivotHeader, false);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldSelectBlockPivotDistanceFromBestPeer(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(1));

    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 5000);

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    final SnapSyncProcessState expected = new SnapSyncProcessState(4000, false);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldConsiderTotalDifficultyWhenSelectingBestPeer(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(1));
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(1000), 5500);
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.of(2000), 4000);

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    final SnapSyncProcessState expected = new SnapSyncProcessState(3000, false);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryUntilMinHeightEstimatesAreAvailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(2));
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));

    SilProtocolManagerTestUtil.disableSilSchedulerAutoRun(silProtocolManager);

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    assertThat(result).isNotDone();

    // First peer is under the threshold, we should keep retrying
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 5000);
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isNotDone();

    // Second peer meets min peer threshold, we should select the pivot
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 5000);
    final SnapSyncProcessState expected = new SnapSyncProcessState(4000, false);
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldRetryIfPivotBlockSelectorReturnsEmptyOptional(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(3));

    PivotBlockSelector pivotBlockSelector = mock(PivotBlockSelector.class);
    pivotSyncActions = createPivotSyncActions(syncConfig, pivotBlockSelector);

    SnapSyncProcessState expectedResult = new SnapSyncProcessState(123, false);

    when(pivotBlockSelector.selectNewPivotBlock())
        .thenReturn(
            CompletableFuture.failedFuture(new RuntimeException("No pivot block available")))
        .thenReturn(CompletableFuture.completedFuture(expectedResult));
    when(pivotBlockSelector.prepareRetry()).thenReturn(CompletableFuture.completedFuture(null));

    CompletableFuture<SnapSyncProcessState> resultFuture =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());

    verify(pivotBlockSelector, times(2)).selectNewPivotBlock();
    verify(pivotBlockSelector).prepareRetry();

    assertThat(resultFuture).isCompletedWithValue(expectedResult);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerWithHeightEstimate(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(3));
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(true, false);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerThatIsValidated(final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(3));
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(false, true);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerThatIsValidatedAndHasHeightEstimate(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(3));
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(true, true);
  }

  private void selectPivotBlockUsesBestPeerMatchingRequiredCriteria(
      final boolean bestMissingHeight, final boolean bestNotValidated) {
    final int peerCount = 4;
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));
    final long minPivotHeight = syncConfig.getSyncPivotDistance() + 1L;
    SilProtocolManagerTestUtil.disableSilSchedulerAutoRun(silProtocolManager);

    // Create peers without chain height estimates
    final PeerValidator validator = mock(PeerValidator.class);
    List<RespondingSilPeer> peers = new ArrayList<>();
    for (int i = 0; i < peerCount; i++) {
      // Best peer by td is the first peer, td decreases as i increases
      final boolean isBest = i == 0;
      final Difficulty td = Difficulty.of(peerCount - i);

      final OptionalLong height;
      if (isBest && bestMissingHeight) {
        // Don't set a height estimate for the best peer
        height = OptionalLong.empty();
      } else {
        // Height increases with i
        height = OptionalLong.of(minPivotHeight + i);
      }

      final RespondingSilPeer peer =
          SilProtocolManagerTestUtil.createPeer(silProtocolManager, td, height, validator);
      if (!isBest || !bestNotValidated) {
        peer.getSilPeer().markValidated(validator);
      }
      peers.add(peer);
    }

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);

    final long expectedBestChainHeight =
        peers.get(1).getSilPeer().chainState().getEstimatedHeight();
    final SnapSyncProcessState expected =
        new SnapSyncProcessState(
            expectedBestChainHeight - syncConfig.getSyncPivotDistance(), false);
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryIfBestPeerChainIsShorterThanPivotDistance(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(1));
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));
    final long pivotDistance = syncConfig.getSyncPivotDistance();

    SilProtocolManagerTestUtil.disableSilSchedulerAutoRun(silProtocolManager);
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, pivotDistance - 1);

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    assertThat(result).isNotDone();
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isNotDone();

    final long validHeight = pivotDistance + 1;
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, validHeight);
    final SnapSyncProcessState expected = new SnapSyncProcessState(1, false);
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void selectPivotBlockShouldRetryIfBestPeerChainIsEqualToPivotDistance(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final long pivotDistance = syncConfig.getSyncPivotDistance();
    SilProtocolManagerTestUtil.disableSilSchedulerAutoRun(silProtocolManager);
    // Create peers with chains that are too short
    for (int i = 0; i < syncConfig.getSyncMinimumPeerCount(); i++) {
      SilProtocolManagerTestUtil.createPeer(silProtocolManager, pivotDistance);
    }

    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.selectPivotBlock(new SnapSyncProcessState());
    assertThat(result).isNotDone();
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isNotDone();

    final long validHeight = pivotDistance + 1;
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, validHeight);
    final SnapSyncProcessState expected = new SnapSyncProcessState(1, false);
    SilProtocolManagerTestUtil.runPendingFutures(silProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldUseExistingPivotBlockHeaderIfPresent(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final SnapSyncProcessState expected = new SnapSyncProcessState(pivotHeader, false);
    assertThat(pivotSyncActions.downloadPivotBlockHeader(expected)).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldRetrievePivotBlockHeader(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(1));
    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(
                silContext,
                syncConfig,
                syncState,
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY));

    final BlockHeader expectedHeader = blockchain.getBlockHeader(1).get();
    final PeerTaskExecutor peerTaskExecutor = silContext.getPeerTaskExecutor();
    when(peerTaskExecutor.executeAgainstPeer(any(), any()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(expectedHeader)),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of()));

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1001);
    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.downloadPivotBlockHeader(new SnapSyncProcessState(1, false));

    assertThat(result).isCompletedWithValue(new SnapSyncProcessState(expectedHeader, false));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotSyncActionsTest.PivotSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldRetrievePivotBlockHash(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat, Optional.of(1));
    GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);
    when(genesisConfig.getTerminalBlockNumber()).thenReturn(OptionalLong.of(10L));

    final Optional<ForkchoiceEvent> finalizedEvent =
        Optional.of(
            new ForkchoiceEvent(
                null,
                blockchain.getBlockHashByNumber(3L).get(),
                blockchain.getBlockHashByNumber(2L).get()));

    final SingleBlockHeaderDownloader headerDownloader =
        new SingleBlockHeaderDownloader(silContext, blockchainSetupUtil.getProtocolSchedule());

    pivotSyncActions =
        createPivotSyncActions(
            syncConfig,
            new PivotSelectorFromSafeBlock(
                blockchainSetupUtil.getProtocolContext(),
                genesisConfig,
                headerDownloader,
                blockchainSetupUtil.getProtocolSchedule(),
                Clock.systemUTC(),
                SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY,
                () -> {}));

    final BlockHeader expectedHeader = blockchain.getBlockHeader(3).get();
    final PeerTaskExecutor peerTaskExecutor = silContext.getPeerTaskExecutor();
    when(peerTaskExecutor.execute(any()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(expectedHeader)),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of()));

    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1001);
    final CompletableFuture<SnapSyncProcessState> result =
        pivotSyncActions.downloadPivotBlockHeader(
            new SnapSyncProcessState(finalizedEvent.get().getSafeBlockHash(), false));

    assertThat(result).isCompletedWithValue(new SnapSyncProcessState(expectedHeader, false));
  }

  private PivotSyncActions createPivotSyncActions(
      final SynchronizerConfiguration syncConfig, final PivotBlockSelector pivotBlockSelector) {
    final ProtocolSchedule protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    final ProtocolContext protocolContext = blockchainSetupUtil.getProtocolContext();
    final SilContext silContext = silProtocolManager.silContext();
    try {
      return new PivotSyncActions(
          syncConfig,
          worldStateStorageCoordinator,
          protocolSchedule,
          protocolContext,
          silContext,
          new SyncState(blockchain, silContext.getSilPeers(), true, Optional.empty()),
          pivotBlockSelector,
          new NoOpMetricsSystem(),
          java.nio.file.Files.createTempDirectory("checkpoint-sync-test"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
