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
package org.hyperledger.besu.sila.blockcreation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.transactions.BlobCache;
import org.hyperledger.besu.sila.sil.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.PendingTransactions;
import org.hyperledger.besu.sila.sil.transactions.TransactionBroadcaster;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.sila.sil.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.sila.sila-mainnet.BalConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.number.Fraction;

import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Disabled;

@Disabled("flaky https://github.com/hyperledger/besu/issues/8238")
public class LegacyFeeMarketBlockTransactionSelectorTest
    extends AbstractBlockTransactionSelectorTest {

  @Override
  protected GenesisConfig getGenesisConfig() {
    return GenesisConfig.fromResource("/block-transaction-selector/gas-price-genesis.json");
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return new ProtocolScheduleBuilder(
            genesisConfig.getConfigOptions(),
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(0, Function.identity()),
            false,
            SavmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem())
        .createProtocolSchedule();
  }

  @Override
  protected TransactionPool createTransactionPool() {
    final TransactionPoolConfiguration poolConf =
        ImmutableTransactionPoolConfiguration.builder()
            .txPoolMaxSize(5)
            .txPoolLimitByAccountPercentage(Fraction.fromFloat(1f))
            .minGasPrice(Wei.ONE)
            .build();

    final PendingTransactions pendingTransactions =
        new GasPricePendingTransactionsSorter(
            poolConf,
            TestClock.system(ZoneId.systemDefault()),
            metricsSystem,
            blockchain::getChainHeadHeader);

    final SilContext silContext = mock(SilContext.class, RETURNS_DEEP_STUBS);
    when(silContext.getSilPeers().subscribeConnect(any())).thenReturn(1L);

    final TransactionPool transactionPool =
        new TransactionPool(
            () -> pendingTransactions,
            protocolSchedule,
            protocolContext,
            mock(TransactionBroadcaster.class),
            silContext,
            new TransactionPoolMetrics(metricsSystem),
            poolConf,
            new BlobCache());
    transactionPool.setEnabled();
    return transactionPool;
  }
}
