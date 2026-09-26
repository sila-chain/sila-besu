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
package org.hyperledger.besu.sila.sil.transactions.layered;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.ExecutionContextTestFixture;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.core.TransactionTestFixture;
import org.hyperledger.besu.sila.sil.transactions.BlobCache;
import org.hyperledger.besu.sila.sil.transactions.PendingTransaction;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.sila.silaMainnet.feemarket.FeeMarket;

import java.util.function.BiFunction;

public class LayeredTransactionPoolGasPriceTest extends AbstractLayeredTransactionPoolTest {

  @Override
  protected AbstractPrioritizedTransactions createPrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics txPoolMetrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    return new GasPricePrioritizedTransactions(
        poolConfig,
        silScheduler,
        nextLayer,
        txPoolMetrics,
        transactionReplacementTester,
        new BlobCache(),
        MiningConfiguration.newDefault(),
        senderBalanceChecker);
  }

  @Override
  protected Transaction createTransaction(final int nonce, final Wei maxPrice) {
    return createTransactionGasPriceMarket(nonce, maxPrice);
  }

  @Override
  protected TransactionTestFixture createBaseTransaction(final int nonce) {
    return createBaseTransactionGasPriceMarket(nonce);
  }

  @Override
  protected ExecutionContextTestFixture createExecutionContextTestFixture() {
    return ExecutionContextTestFixture.create();
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.legacy();
  }

  @Override
  protected Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    return appendBlockGasPriceMarket(difficulty, parentBlock, transactionsToAdd);
  }
}
