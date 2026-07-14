/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.sila.api.ApiConfiguration;
import org.hyperledger.besu.sila.api.jsonrpc.RpcApis;
import org.hyperledger.besu.sila.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilAccounts;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilBaseFee;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilBlobBaseFee;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilBlockNumber;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilCall;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilCapabilities;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilChainId;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilConfig;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilCreateAccessList;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilEstimateGas;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilFeeHistory;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGasPrice;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBalance;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockAccessList;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockByHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockByNumber;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockReceipts;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockTransactionCountByHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetBlockTransactionCountByNumber;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetCode;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetFilterChanges;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetFilterLogs;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetLogs;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetProof;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetStorageAt;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetStorageValues;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionByBlockHashAndIndex;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionByBlockNumberAndIndex;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionByHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionBySenderAndNonce;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionCount;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetTransactionReceipt;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetUncleByBlockHashAndIndex;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetUncleByBlockNumberAndIndex;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetUncleCountByBlockHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilGetUncleCountByBlockNumber;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilMaxPriorityFeePerGas;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilNewBlockFilter;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilNewFilter;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilNewPendingTransactionFilter;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilProtocolVersion;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilSendRawTransaction;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilSendTransaction;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilSimulateV1;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilSyncing;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.SilUninstallFilter;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.blockcreation.MiningCoordinator;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Map;
import java.util.Set;

public class SilJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockResultFactory blockResult = new BlockResultFactory();

  private final BlockchainQueries blockchainQueries;
  private final Synchronizer synchronizer;
  private final ProtocolSchedule protocolSchedule;
  private final FilterManager filterManager;
  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final MiningConfiguration miningConfiguration;

  private final Set<Capability> supportedCapabilities;
  private final ApiConfiguration apiConfiguration;
  private final GenesisConfigOptions genesisConfigOptions;
  private final TransactionSimulator transactionSimulator;
  private final ServiceManager serviceManager;
  private final MetricsSystem metricsSystem;

  public SilJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final ProtocolSchedule protocolSchedule,
      final FilterManager filterManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final MiningConfiguration miningConfiguration,
      final Set<Capability> supportedCapabilities,
      final ApiConfiguration apiConfiguration,
      final GenesisConfigOptions genesisConfigOptions,
      final TransactionSimulator transactionSimulator,
      final ServiceManager serviceManager,
      final MetricsSystem metricsSystem) {
    this.blockchainQueries = blockchainQueries;
    this.synchronizer = synchronizer;
    this.protocolSchedule = protocolSchedule;
    this.filterManager = filterManager;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.miningConfiguration = miningConfiguration;
    this.supportedCapabilities = supportedCapabilities;
    this.apiConfiguration = apiConfiguration;
    this.genesisConfigOptions = genesisConfigOptions;
    this.transactionSimulator = transactionSimulator;
    this.serviceManager = serviceManager;
    this.metricsSystem = metricsSystem;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.SIL.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final Map<String, JsonRpcMethod> map =
        mapOf(
            new SilAccounts(),
            new SilBlockNumber(blockchainQueries),
            new SilGetBalance(blockchainQueries),
            new SilGetBlockByHash(blockchainQueries, blockResult),
            new SilGetBlockByNumber(blockchainQueries, blockResult, synchronizer),
            new SilGetBlockReceipts(blockchainQueries, protocolSchedule),
            new SilGetBlockTransactionCountByNumber(blockchainQueries),
            new SilGetBlockTransactionCountByHash(blockchainQueries),
            new SilCall(blockchainQueries, transactionSimulator, metricsSystem),
            new SilFeeHistory(
                protocolSchedule, blockchainQueries, miningCoordinator, apiConfiguration),
            new SilGetCode(blockchainQueries),
            new SilGetLogs(blockchainQueries, apiConfiguration.getMaxLogsRange()),
            new SilGetProof(blockchainQueries),
            new SilGetUncleCountByBlockHash(blockchainQueries),
            new SilGetUncleCountByBlockNumber(blockchainQueries),
            new SilGetUncleByBlockNumberAndIndex(blockchainQueries),
            new SilGetUncleByBlockHashAndIndex(blockchainQueries),
            new SilNewBlockFilter(filterManager),
            new SilNewPendingTransactionFilter(filterManager),
            new SilNewFilter(filterManager),
            new SilGetTransactionByHash(blockchainQueries, transactionPool),
            new SilGetTransactionByBlockHashAndIndex(blockchainQueries),
            new SilGetTransactionByBlockNumberAndIndex(blockchainQueries),
            new SilGetTransactionBySenderAndNonce(blockchainQueries, transactionPool),
            new SilGetTransactionCount(blockchainQueries, transactionPool),
            new SilGetTransactionReceipt(blockchainQueries, protocolSchedule),
            new SilUninstallFilter(filterManager),
            new SilGetFilterChanges(filterManager),
            new SilGetFilterLogs(filterManager),
            new SilSyncing(synchronizer),
            new SilGetStorageAt(blockchainQueries),
            new SilGetStorageValues(blockchainQueries),
            new SilSendRawTransaction(transactionPool),
            new SilSendTransaction(),
            new SilEstimateGas(blockchainQueries, transactionSimulator, apiConfiguration),
            new SilCreateAccessList(blockchainQueries, transactionSimulator),
            new SilCapabilities(blockchainQueries),
            new SilConfig(blockchainQueries, protocolSchedule, genesisConfigOptions),
            new SilProtocolVersion(supportedCapabilities),
            new SilGasPrice(blockchainQueries, apiConfiguration),
            new SilChainId(protocolSchedule.getChainId()),
            new SilBaseFee(blockchainQueries),
            new SilBlobBaseFee(blockchainQueries.getBlockchain(), protocolSchedule),
            new SilMaxPriorityFeePerGas(blockchainQueries),
            new SilSimulateV1(
                serviceManager,
                blockchainQueries,
                protocolSchedule,
                transactionSimulator,
                miningConfiguration,
                apiConfiguration),
            new SilGetBlockAccessList(blockchainQueries));
    return map;
  }
}
