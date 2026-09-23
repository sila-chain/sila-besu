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
package org.hyperledger.besu.sila.api.jsonrpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.promsileus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.api.ApiConfiguration;
import org.hyperledger.besu.sila.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.sila.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.sila.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.sila.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.ChainHead;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.p2p.network.P2PNetwork;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.sila.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetProtocolSchedule;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;

public class JsonRpcHttpServiceTestBase {

  // this tempDir is deliberately static
  @TempDir private static Path folder;
  protected final JsonRpcTestHelper testHelper = new JsonRpcTestHelper();

  private static final Vertx vertx = Vertx.vertx();
  protected static Map<String, JsonRpcMethod> rpcMethods;
  private static Map<String, JsonRpcMethod> disabledRpcMethods;
  private static Set<String> addedRpcMethods;
  protected static JsonRpcHttpService service;
  protected static OkHttpClient client;
  protected static String baseUrl;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final String CLIENT_NODE_NAME = "TestClientVersion/0.1.0";
  protected static final String CLIENT_VERSION = "0.1.0";
  protected static final String CLIENT_COMMIT = "12345678";
  protected static final BigInteger CHAIN_ID = BigInteger.valueOf(123);
  protected static P2PNetwork peerDiscoveryMock;
  protected static SilPeers silPeersMock;
  protected static Blockchain blockchain;
  protected static BlockchainQueries blockchainQueries;
  protected static ChainHead chainHead;
  protected static Block block;
  protected static Synchronizer synchronizer;
  protected static final Collection<String> JSON_RPC_APIS =
      Arrays.asList(
          RpcApis.SIL.name(), RpcApis.NET.name(), RpcApis.WEB3.name(), RpcApis.ADMIN.name());
  protected static final NatService natService = new NatService(Optional.empty());
  protected static int maxConnections = 80;
  protected static int maxBatchSize = 10;

  public static void initServerAndClient() throws Exception {
    peerDiscoveryMock = mock(P2PNetwork.class);
    silPeersMock = mock(SilPeers.class);
    blockchain = mock(Blockchain.class);
    blockchainQueries = mock(BlockchainQueries.class);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    block = mock(Block.class);
    when(blockchain.getGenesisBlock()).thenReturn(block);
    when(block.getHash()).thenReturn(Hash.EMPTY);
    chainHead = mock(ChainHead.class);
    synchronizer = mock(Synchronizer.class);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(SilProtocol.LATEST);

    rpcMethods =
        new JsonRpcMethodsFactory()
            .methods(
                CLIENT_NODE_NAME,
                CLIENT_VERSION,
                CLIENT_COMMIT,
                CHAIN_ID,
                new StubGenesisConfigOptions(),
                peerDiscoveryMock,
                blockchainQueries,
                synchronizer,
                SilaMainnetProtocolSchedule.fromConfig(
                    new StubGenesisConfigOptions().constantinopleBlock(0).chainId(CHAIN_ID),
                    SavmConfiguration.DEFAULT,
                    MiningConfiguration.MINING_DISABLED,
                    new BadBlockManager(),
                    false,
                    BalConfiguration.DEFAULT,
                    new NoOpMetricsSystem()),
                mock(ProtocolContext.class),
                mock(FilterManager.class),
                mock(TransactionPool.class),
                mock(MiningConfiguration.class),
                new NoopMiningCoordinator(),
                new NoOpMetricsSystem(),
                supportedCapabilities,
                Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                JSON_RPC_APIS,
                mock(JsonRpcConfiguration.class),
                mock(WebSocketConfiguration.class),
                mock(MetricsConfiguration.class),
                mock(GraphQLConfiguration.class),
                natService,
                new HashMap<>(),
                folder,
                silPeersMock,
                vertx,
                mock(ApiConfiguration.class),
                Optional.empty(),
                mock(TransactionSimulator.class),
                new DeterministicSilScheduler());
    disabledRpcMethods = new HashMap<>();
    addedRpcMethods = new HashSet<>();

    service = createJsonRpcHttpService(createLimitedJsonRpcConfig());
    service.start().join();

    // Build an OkHttp client.
    client = new OkHttpClient();
    baseUrl = service.url();
  }

  protected static JsonRpcHttpService createJsonRpcHttpService(final JsonRpcConfiguration config) {
    return new JsonRpcHttpService(
        vertx,
        folder,
        config,
        new NoOpMetricsSystem(),
        natService,
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  protected static JsonRpcHttpService createJsonRpcHttpService() {
    return new JsonRpcHttpService(
        vertx,
        folder,
        createLimitedJsonRpcConfig(),
        new NoOpMetricsSystem(),
        natService,
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcConfiguration createLimitedJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsAllowlist(Collections.singletonList("*"));
    config.setMaxActiveConnections(maxConnections);
    config.setMaxBatchSize(maxBatchSize);
    return config;
  }

  protected Request buildPostRequest(final RequestBody body) {
    return new Request.Builder().post(body).url(baseUrl).build();
  }

  protected Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }

  protected AutoCloseable disableRpcMethod(final String methodName) {
    disabledRpcMethods.put(methodName, rpcMethods.remove(methodName));
    return () -> resetRpcMethods();
  }

  protected AutoCloseable addRpcMethod(final String methodName, final JsonRpcMethod method) {
    rpcMethods.put(methodName, method);
    addedRpcMethods.add(methodName);
    return () -> resetRpcMethods();
  }

  protected void resetRpcMethods() {
    disabledRpcMethods.forEach(rpcMethods::put);
    addedRpcMethods.forEach(rpcMethods::remove);
  }

  /** Tears down the HTTP server. */
  @AfterAll
  public static void shutdownServer() {
    service.stop().join();
  }
}
