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
package org.hyperledger.besu.sila.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.sila.core.ProtocolScheduleFixture.getGenesisConfigOptions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.api.ApiConfiguration;
import org.hyperledger.besu.sila.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.sila.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.sila.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.BalConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.p2p.network.P2PNetwork;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.sila.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.promsileus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonRpcMethodsFactoryTest {
  private static final String CLIENT_NODE_NAME = "TestClientVersion/0.1.0";
  private static final String CLIENT_VERSION = "0.1.0";
  private static final String CLIENT_COMMIT = "12345678";
  private static final BigInteger NETWORK_ID = BigInteger.valueOf(123);

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private MergeMiningCoordinator mergeCoordinator;

  @TempDir private Path folder;

  final Set<Capability> supportedCapabilities = new HashSet<>();
  private final NatService natService = new NatService(Optional.empty());
  private final Vertx vertx = Vertx.vertx();

  private ProtocolSchedule pragueAllMilestonesZeroProtocolSchedule;
  private JsonRpcConfiguration configuration;

  @BeforeEach
  public void setup() {
    configuration = JsonRpcConfiguration.createEngineDefault();
    configuration.setPort(0);

    Blockchain blockchain = mock(Blockchain.class);
    Block block = mock(Block.class);
    lenient().when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    lenient().when(blockchain.getGenesisBlock()).thenReturn(block);
    lenient().when(block.getHash()).thenReturn(Hash.EMPTY);

    pragueAllMilestonesZeroProtocolSchedule =
        SilaMainnetProtocolSchedule.fromConfig(
            getSilaPragueAllZeroMilestonesConfigOptions(),
            Optional.empty(),
            Optional.empty(),
            MiningConfiguration.newDefault(),
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem());

    when(mergeCoordinator.isCompatibleWithEngineApi()).thenReturn(true);
  }

  @Test
  public void shouldActivateEngineApisIfMilestonesAreAllZero() {
    final Map<String, JsonRpcMethod> rpcMethods =
        new JsonRpcMethodsFactory()
            .methods(
                CLIENT_NODE_NAME,
                CLIENT_VERSION,
                CLIENT_COMMIT,
                NETWORK_ID,
                new StubGenesisConfigOptions(),
                mock(P2PNetwork.class),
                blockchainQueries,
                mock(Synchronizer.class),
                pragueAllMilestonesZeroProtocolSchedule,
                mock(ProtocolContext.class),
                mock(FilterManager.class),
                mock(TransactionPool.class),
                mock(MiningConfiguration.class),
                mergeCoordinator,
                new NoOpMetricsSystem(),
                supportedCapabilities,
                Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                configuration.getRpcApis(),
                mock(JsonRpcConfiguration.class),
                mock(WebSocketConfiguration.class),
                mock(MetricsConfiguration.class),
                mock(GraphQLConfiguration.class),
                natService,
                new HashMap<>(),
                folder,
                mock(SilPeers.class),
                vertx,
                mock(ApiConfiguration.class),
                Optional.empty(),
                mock(TransactionSimulator.class),
                new DeterministicSilScheduler());

    assertThat(rpcMethods).containsKey("engine_getPayloadV3");
    assertThat(rpcMethods).containsKey("engine_getPayloadV4");
    assertThat(rpcMethods).containsKey("engine_newPayloadV4");
  }

  private GenesisConfigOptions getSilaPragueAllZeroMilestonesConfigOptions() {
    return getGenesisConfigOptions("/prague_all_milestones_zero.json");
  }
}
