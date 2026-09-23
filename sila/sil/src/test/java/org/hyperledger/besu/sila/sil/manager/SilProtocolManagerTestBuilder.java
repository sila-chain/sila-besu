/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.sila.sil.manager;

import static org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.GenesisState;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidator;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.trie.pathbased.common.code.PathBasedCodeCache;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class SilProtocolManagerTestBuilder {
  private static final BigInteger DEFAULT_NETWORK_ID = BigInteger.ONE;

  private ProtocolSchedule protocolSchedule;
  private GenesisConfig genesisConfig;
  private GenesisState genesisState;
  private Blockchain blockchain;
  private BigInteger networkId;
  private WorldStateArchive worldStateArchive;
  private TransactionPool transactionPool;
  private SilProtocolConfiguration silaWireProtocolConfiguration;
  private ForkIdManager forkIdManager;
  private SilPeers silPeers;
  private SilMessages silMessages;
  private SilMessages snapMessages;
  private SilScheduler silScheduler;
  private SilContext silContext;
  private List<PeerValidator> peerValidators;
  private Optional<MergePeerFilter> mergePeerFilter = Optional.empty();
  private SynchronizerConfiguration synchronizerConfiguration;
  private PeerTaskExecutor peerTaskExecutor;

  public static SilProtocolManagerTestBuilder builder() {
    return new SilProtocolManagerTestBuilder();
  }

  public SilProtocolManagerTestBuilder setProtocolSchedule(
      final ProtocolSchedule protocolSchedule) {
    this.protocolSchedule = protocolSchedule;
    return this;
  }

  public SilProtocolManagerTestBuilder setGenesisConfigFile(final GenesisConfig genesisConfig) {
    this.genesisConfig = genesisConfig;
    return this;
  }

  public SilProtocolManagerTestBuilder setGenesisState(final GenesisState genesisState) {
    this.genesisState = genesisState;
    return this;
  }

  public SilProtocolManagerTestBuilder setBlockchain(final Blockchain blockchain) {
    this.blockchain = blockchain;
    return this;
  }

  public SilProtocolManagerTestBuilder setNetworkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  public SilProtocolManagerTestBuilder setWorldStateArchive(
      final WorldStateArchive worldStateArchive) {
    this.worldStateArchive = worldStateArchive;
    return this;
  }

  public SilProtocolManagerTestBuilder setTransactionPool(final TransactionPool transactionPool) {
    this.transactionPool = transactionPool;
    return this;
  }

  public SilProtocolManagerTestBuilder setSilaWireProtocolConfiguration(
      final SilProtocolConfiguration silaWireProtocolConfiguration) {
    this.silaWireProtocolConfiguration = silaWireProtocolConfiguration;
    return this;
  }

  public SilProtocolManagerTestBuilder setForkIdManager(final ForkIdManager forkIdManager) {
    this.forkIdManager = forkIdManager;
    return this;
  }

  public SilProtocolManagerTestBuilder setSilPeers(final SilPeers silPeers) {
    this.silPeers = silPeers;
    return this;
  }

  public SilProtocolManagerTestBuilder setSilMessages(final SilMessages silMessages) {
    this.silMessages = silMessages;
    return this;
  }

  public SilProtocolManagerTestBuilder setSnapMessages(final SilMessages snapMessages) {
    this.snapMessages = snapMessages;
    return this;
  }

  public SilProtocolManagerTestBuilder setSilContext(final SilContext silContext) {
    this.silContext = silContext;
    return this;
  }

  public SilProtocolManagerTestBuilder setPeerValidators(final List<PeerValidator> peerValidators) {
    this.peerValidators = peerValidators;
    return this;
  }

  public SilProtocolManagerTestBuilder setMergePeerFilter(
      final Optional<MergePeerFilter> mergePeerFilter) {
    this.mergePeerFilter = mergePeerFilter;
    return this;
  }

  public SilProtocolManagerTestBuilder setSynchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfiguration) {
    this.synchronizerConfiguration = synchronizerConfiguration;
    return this;
  }

  public SilProtocolManagerTestBuilder setSilScheduler(final SilScheduler silScheduler) {
    this.silScheduler = silScheduler;
    return this;
  }

  public SilProtocolManagerTestBuilder setPeerTaskExecutor(
      final PeerTaskExecutor peerTaskExecutor) {
    this.peerTaskExecutor = peerTaskExecutor;
    return this;
  }

  public SilProtocolManager build() {
    if (protocolSchedule == null) {
      protocolSchedule = ProtocolScheduleFixture.TESTING_NETWORK;
    }
    if (blockchain == null) {
      if (genesisConfig == null) {
        genesisConfig = GenesisConfig.sila - mainnet();
      }
      if (genesisState == null) {
        genesisState =
            GenesisState.fromConfig(genesisConfig, protocolSchedule, new PathBasedCodeCache());
      }
      blockchain = createInMemoryBlockchain(genesisState.getBlock());
    }
    if (networkId == null) {
      networkId = DEFAULT_NETWORK_ID;
    }
    if (worldStateArchive == null) {
      worldStateArchive =
          BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST).getWorldArchive();
    }
    if (transactionPool == null) {
      transactionPool = mock(TransactionPool.class);
    }
    if (silaWireProtocolConfiguration == null) {
      silaWireProtocolConfiguration = SilProtocolConfiguration.DEFAULT;
    }
    if (forkIdManager == null) {
      forkIdManager =
          new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList());
    }
    if (silPeers == null) {
      silPeers =
          new SilPeers(
              () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()),
              TestClock.fixed(),
              new NoOpMetricsSystem(),
              SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
              Collections.emptyList(),
              Bytes.random(64),
              25,
              25,
              false,
              SyncMode.SNAP,
              forkIdManager);
    }
    silPeers.setChainHeadTracker(SilProtocolManagerTestUtil.getChainHeadTrackerMock());
    if (silMessages == null) {
      silMessages = new SilMessages();
    }
    if (snapMessages == null) {
      snapMessages = new SilMessages();
    }
    if (silScheduler == null) {
      silScheduler =
          new DeterministicSilScheduler(DeterministicSilScheduler.TimeoutPolicy.NEVER_TIMEOUT);
    }
    if (peerTaskExecutor == null) {
      peerTaskExecutor = mock(PeerTaskExecutor.class);
    }
    if (silContext == null) {
      silContext =
          new SilContext(silPeers, silMessages, snapMessages, silScheduler, peerTaskExecutor);
    }
    if (peerValidators == null) {
      peerValidators = Collections.emptyList();
    }
    if (mergePeerFilter.isEmpty()) {
      mergePeerFilter = Optional.of(new MergePeerFilter());
    }
    if (synchronizerConfiguration == null) {
      synchronizerConfiguration = SynchronizerConfiguration.builder().build();
    }
    return new SilProtocolManager(
        blockchain,
        networkId,
        worldStateArchive,
        transactionPool,
        silaWireProtocolConfiguration,
        silPeers,
        silMessages,
        silContext,
        peerValidators,
        mergePeerFilter,
        synchronizerConfiguration,
        silScheduler,
        forkIdManager);
  }
}
