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
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.SilContext;
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

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FullSyncChainDownloaderForkTest {

  protected ProtocolSchedule protocolSchedule;
  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;
  protected ProtocolContext protocolContext;
  private SyncState syncState;
  private PeerTaskExecutor peerTaskExecutor;

  private BlockchainSetupUtil localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @BeforeEach
  public void setupTest() throws IOException {
    localBlockchainSetup = BlockchainSetupUtil.forUpgradedFork();
    localBlockchain = localBlockchainSetup.getBlockchain();
    otherBlockchainSetup = BlockchainSetupUtil.forOutdatedFork();
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

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(
            new GetHeadersFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetBodiesFromPeerTask.class)))
        .thenAnswer(
            new GetBodiesFromPeerTaskExecutorAnswer(otherBlockchain, silContext.getSilPeers()));
  }

  @AfterEach
  public void tearDown() {
    silProtocolManager.stop();
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

  @Test
  public void disconnectsFromPeerOnBadFork() {
    otherBlockchainSetup.importAllBlocks();

    final RespondingSilPeer.Responder responder =
        RespondingSilPeer.blockchainResponder(otherBlockchain);
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 100);

    final ChainDownloader downloader = downloader();
    downloader.start();

    // Process until the sync target is selected
    peer.respondWhileOtherThreadsWork(responder, () -> syncState.syncTarget().isEmpty());

    // Check that we picked our peer
    assertThat(syncState.syncTarget()).isPresent();
    assertThat(syncState.syncTarget().get().peer()).isEqualTo(peer.getSilPeer());

    // Process until the sync target is cleared
    peer.respondWhileOtherThreadsWork(responder, () -> syncState.syncTarget().isPresent());

    // We should have disconnected from our peer on the invalid chain
    assertThat(peer.getSilPeer().isDisconnected()).isTrue();
    assertThat(peer.getPeerConnection().getDisconnectReason())
        .contains(DisconnectReason.BREACH_OF_PROTOCOL_INVALID_BLOCK);
    assertThat(syncState.syncTarget()).isEmpty();
  }
}
