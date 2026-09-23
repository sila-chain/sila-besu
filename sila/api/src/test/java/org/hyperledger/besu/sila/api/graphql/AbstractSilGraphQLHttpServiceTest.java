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
package org.hyperledger.besu.sila.api.graphql;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.api.ImmutableApiConfiguration;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.DefaultSyncStatus;
import org.hyperledger.besu.sila.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.transactions.PendingTransaction;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.silaMainnet.ValidationResult;
import org.hyperledger.besu.sila.transaction.TransactionInvalidReason;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public abstract class AbstractSilGraphQLHttpServiceTest {
  @TempDir private Path tempDir;

  private static BlockchainSetupUtil blockchainSetupUtil;

  private final Vertx vertx = Vertx.vertx();

  private GraphQLHttpService service;

  OkHttpClient client;

  String baseUrl;

  final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  @BeforeAll
  public static void setupConstants() {
    blockchainSetupUtil = BlockchainSetupUtil.forHiveTesting(DataStorageFormat.BONSAI);
    blockchainSetupUtil.importAllBlocks();
  }

  @BeforeEach
  public void setupTest() throws Exception {
    final Synchronizer synchronizerMock = mock(Synchronizer.class);
    final SyncStatus status = new DefaultSyncStatus(1, 2, 3, Optional.of(4L), Optional.of(5L));
    when(synchronizerMock.getSyncStatus()).thenReturn(Optional.of(status));

    final TransactionPool transactionPoolMock = mock(TransactionPool.class);

    when(transactionPoolMock.addTransactionViaApi(ArgumentMatchers.any(Transaction.class)))
        .thenReturn(ValidationResult.valid());
    // nonce too low tests uses a tx with nonce=16
    when(transactionPoolMock.addTransactionViaApi(
            ArgumentMatchers.argThat(tx -> tx.getNonce() == 16)))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_LOW));
    Mockito.when(transactionPoolMock.getPendingTransactions())
        .thenReturn(
            Collections.singleton(
                new PendingTransaction.Local(
                    Transaction.builder()
                        .type(TransactionType.FRONTIER)
                        .nonce(42)
                        .gasLimit(654321)
                        .gasPrice(Wei.ONE)
                        .build())));

    final MutableBlockchain blockchain = blockchainSetupUtil.getBlockchain();
    ProtocolContext context =
        new ProtocolContext.Builder()
            .withBlockchain(blockchain)
            .withWorldStateArchive(blockchainSetupUtil.getWorldArchive())
            .build();
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            blockchainSetupUtil.getProtocolSchedule(),
            context.getBlockchain(),
            context.getWorldStateArchive(),
            Optional.empty(),
            Optional.empty(),
            ImmutableApiConfiguration.builder().build(),
            MiningConfiguration.newDefault().setMinTransactionGasPrice(Wei.ZERO));

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(SilProtocol.LATEST);

    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();

    config.setPort(0);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    final var transactionSimulator =
        new TransactionSimulator(
            blockchain,
            blockchainSetupUtil.getWorldArchive(),
            blockchainSetupUtil.getProtocolSchedule(),
            ImmutableMiningConfiguration.newDefault(),
            0L);

    service =
        new GraphQLHttpService(
            vertx,
            tempDir,
            config,
            graphQL,
            Map.of(
                GraphQLContextType.BLOCKCHAIN_QUERIES,
                blockchainQueries,
                GraphQLContextType.PROTOCOL_SCHEDULE,
                blockchainSetupUtil.getProtocolSchedule(),
                GraphQLContextType.TRANSACTION_POOL,
                transactionPoolMock,
                GraphQLContextType.MINING_COORDINATOR,
                new NoopMiningCoordinator(),
                GraphQLContextType.SYNCHRONIZER,
                synchronizerMock,
                GraphQLContextType.TRANSACTION_SIMULATOR,
                transactionSimulator),
            mock(SilScheduler.class));
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url() + "/graphql/";
  }

  @AfterEach
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }
}
