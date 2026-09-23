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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.api.ApiConfiguration;
import org.hyperledger.besu.sila.api.jsonrpc.RpcApis;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceBlock;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceCall;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceCallMany;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceFilter;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceGet;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceRawTransaction;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceReplayBlockTransactions;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.TraceTransaction;
import org.hyperledger.besu.sila.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.sila.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;

import java.util.Map;

public class TraceJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private final ApiConfiguration apiConfiguration;
  private final ProtocolContext protocolContext;
  private final TransactionSimulator transactionSimulator;
  private final MetricsSystem metricsSystem;
  private final SilScheduler silScheduler;

  TraceJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final ApiConfiguration apiConfiguration,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem,
      final SilScheduler silScheduler) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.apiConfiguration = apiConfiguration;
    this.transactionSimulator = transactionSimulator;
    this.metricsSystem = metricsSystem;
    this.silScheduler = silScheduler;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.TRACE.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final BlockReplay blockReplay =
        new BlockReplay(protocolSchedule, protocolContext, blockchainQueries.getBlockchain());
    return mapOf(
        new TraceReplayBlockTransactions(
            protocolSchedule, blockchainQueries, metricsSystem, silScheduler),
        new TraceFilter(
            protocolSchedule,
            blockchainQueries,
            apiConfiguration.getMaxTraceFilterRange(),
            metricsSystem,
            silScheduler),
        new TraceGet(() -> new BlockTracer(blockReplay), blockchainQueries, protocolSchedule),
        new TraceTransaction(
            () -> new BlockTracer(blockReplay), protocolSchedule, blockchainQueries),
        new TraceBlock(protocolSchedule, blockchainQueries, metricsSystem, silScheduler),
        new TraceCall(blockchainQueries, protocolSchedule, transactionSimulator),
        new TraceCallMany(blockchainQueries, protocolSchedule, transactionSimulator),
        new TraceRawTransaction(protocolSchedule, blockchainQueries, transactionSimulator));
  }
}
