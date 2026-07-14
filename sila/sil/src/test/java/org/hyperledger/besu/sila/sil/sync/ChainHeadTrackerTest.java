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
package org.hyperledger.besu.sila.sil.sync;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sila-mainnet.BalConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class ChainHeadTrackerTest {

  private BlockchainSetupUtil blockchainSetupUtil;
  private MutableBlockchain blockchain;
  private SilProtocolManager silProtocolManager;
  private RespondingSilPeer respondingPeer;

  private PeerTaskExecutor peerTaskExecutor;

  private ChainHeadTracker chainHeadTracker;

  private final ProtocolSchedule protocolSchedule =
      FixedDifficultyProtocolSchedule.create(
          GenesisConfig.fromResource("/dev.json").getConfigOptions(),
          false,
          SavmConfiguration.DEFAULT,
          MiningConfiguration.MINING_DISABLED,
          new BadBlockManager(),
          false,
          BalConfiguration.DEFAULT,
          new NoOpMetricsSystem());

  static class ChainHeadTrackerTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setup(final DataStorageFormat storageFormat) {
    blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchain = blockchainSetupUtil.getBlockchain();
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setBlockchain(blockchain)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    respondingPeer =
        RespondingSilPeer.builder()
            .silProtocolManager(silProtocolManager)
            .chainHeadHash(blockchain.getChainHeadHash())
            .totalDifficulty(blockchain.getChainHead().getTotalDifficulty())
            .estimatedHeight(0)
            .build();
    GetHeadersFromPeerTaskExecutorAnswer getHeadersAnswer =
        new GetHeadersFromPeerTaskExecutorAnswer(
            blockchain, silProtocolManager.silContext().getSilPeers());
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(getHeadersAnswer);
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(getHeadersAnswer);
    chainHeadTracker = new ChainHeadTracker(silProtocolManager.silContext(), protocolSchedule);
  }

  @ParameterizedTest
  @ArgumentsSource(ChainHeadTrackerTestArguments.class)
  public void shouldRequestHeaderChainHeadWhenNewPeerConnects(
      final DataStorageFormat storageFormat) {
    setup(storageFormat);
    chainHeadTracker.getBestHeaderFromPeer(respondingPeer.getSilPeer());

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();
    Assertions.assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  @ParameterizedTest
  @ArgumentsSource(ChainHeadTrackerTestArguments.class)
  public void shouldIgnoreHeadersIfChainHeadHasAlreadyBeenUpdatedWhileWaiting(
      final DataStorageFormat storageFormat) {
    setup(storageFormat);
    chainHeadTracker.getBestHeaderFromPeer(respondingPeer.getSilPeer());

    // Change the hash of the current known head
    respondingPeer.getSilPeer().chainState().statusReceived(Hash.EMPTY_TRIE_HASH, Difficulty.ONE);

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();
  }

  @ParameterizedTest
  @ArgumentsSource(ChainHeadTrackerTestArguments.class)
  public void shouldCheckTrialingPeerLimits(final DataStorageFormat storageFormat) {
    setup(storageFormat);
    chainHeadTracker.getBestHeaderFromPeer(respondingPeer.getSilPeer());

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();
    Assertions.assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  private ChainState chainHeadState() {
    return respondingPeer.getSilPeer().chainState();
  }
}
