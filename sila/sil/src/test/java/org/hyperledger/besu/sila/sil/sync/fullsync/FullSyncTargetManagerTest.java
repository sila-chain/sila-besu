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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.state.SyncTarget;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class FullSyncTargetManagerTest {

  private SilProtocolManager silProtocolManager;

  private MutableBlockchain localBlockchain;
  private final WorldStateArchive localWorldState = mock(WorldStateArchive.class);
  private RespondingSilPeer.Responder responder;
  private FullSyncTargetManager syncTargetManager;
  private PeerTaskExecutor peerTaskExecutor;

  static class FullSyncTargetManagerTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setup(final DataStorageFormat storageFormat) {
    final BlockchainSetupUtil otherBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    final Blockchain otherBlockchain = otherBlockchainSetup.getBlockchain();
    responder = RespondingSilPeer.blockchainResponder(otherBlockchain);

    final BlockchainSetupUtil localBlockchainSetup = BlockchainSetupUtil.forTesting(storageFormat);
    localBlockchain = localBlockchainSetup.getBlockchain();

    final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.TESTING_NETWORK;
    final ProtocolContext protocolContext =
        new ProtocolContext.Builder()
            .withBlockchain(localBlockchain)
            .withWorldStateArchive(localWorldState)
            .build();
    peerTaskExecutor = mock(PeerTaskExecutor.class);
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
    final SilContext silContext = silProtocolManager.silContext();
    localBlockchainSetup.importFirstBlocks(5);
    otherBlockchainSetup.importFirstBlocks(20);
    syncTargetManager =
        new FullSyncTargetManager(
            SynchronizerConfiguration.builder().build(),
            protocolSchedule,
            protocolContext,
            silContext,
            new NoOpMetricsSystem(),
            SyncTerminationCondition.never());

    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(
            new GetHeadersFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers()));
  }

  @AfterEach
  public void tearDown() {
    if (silProtocolManager != null) {
      silProtocolManager.stop();
    }
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncTargetManagerTest.FullSyncTargetManagerTestArguments.class)
  public void findSyncTarget_withHeightEstimates(final DataStorageFormat storageFormat)
      throws ExecutionException, InterruptedException, TimeoutException {
    setup(storageFormat);
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingSilPeer bestPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, Difficulty.MAX_VALUE, 4);
    Mockito.reset(peerTaskExecutor);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(localBlockchain.getBlockHeader(4L).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(bestPeer.getSilPeer())));

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();

    SyncTarget resultSyncTarget = result.get(5, TimeUnit.SECONDS);
    assertThat(resultSyncTarget)
        .isEqualTo(new SyncTarget(bestPeer.getSilPeer(), localBlockchain.getBlockHeader(4L).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncTargetManagerTest.FullSyncTargetManagerTestArguments.class)
  public void findSyncTarget_noHeightEstimates(final DataStorageFormat storageFormat) {
    setup(storageFormat);
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingSilPeer bestPeer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();
    bestPeer.respond(responder);

    assertThat(result).isNotCompleted();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncTargetManagerTest.FullSyncTargetManagerTestArguments.class)
  public void shouldDisconnectPeerIfWorldStateIsUnavailableForCommonAncestor(
      final DataStorageFormat storageFormat) {
    setup(storageFormat);
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(false);
    final RespondingSilPeer bestPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();

    Awaitility.await()
        .atMost(1, TimeUnit.SECONDS)
        .until(() -> bestPeer.getPeerConnection().isDisconnected());
    assertThat(result).isNotCompleted();
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isTrue();
  }

  @ParameterizedTest
  @ArgumentsSource(FullSyncTargetManagerTest.FullSyncTargetManagerTestArguments.class)
  public void shouldAllowSyncTargetWhenIfWorldStateIsAvailableForCommonAncestor(
      final DataStorageFormat storageFormat)
      throws ExecutionException, InterruptedException, TimeoutException {
    setup(storageFormat);
    final BlockHeader chainHeadHeader = localBlockchain.getChainHeadHeader();
    when(localWorldState.isWorldStateAvailable(
            chainHeadHeader.getStateRoot(), chainHeadHeader.getHash()))
        .thenReturn(true);
    final RespondingSilPeer bestPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 20);

    final CompletableFuture<SyncTarget> result = syncTargetManager.findSyncTarget();

    SyncTarget resultSyncTarget = result.get(1, TimeUnit.SECONDS);

    assertThat(resultSyncTarget)
        .isEqualTo(new SyncTarget(bestPeer.getSilPeer(), localBlockchain.getChainHeadHeader()));
    assertThat(bestPeer.getPeerConnection().isDisconnected()).isFalse();
  }
}
