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
package org.hyperledger.besu.sila.sil.transactions;

import static org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration.Implementation.LAYERED;

import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.layered.AbstractPrioritizedTransactions;
import org.hyperledger.besu.sila.sil.transactions.layered.BaseFeePrioritizedTransactions;
import org.hyperledger.besu.sila.sil.transactions.layered.EndLayer;
import org.hyperledger.besu.sila.sil.transactions.layered.GasPricePrioritizedTransactions;
import org.hyperledger.besu.sila.sil.transactions.layered.LayeredPendingTransactions;
import org.hyperledger.besu.sila.sil.transactions.layered.ReadyTransactions;
import org.hyperledger.besu.sila.sil.transactions.layered.SenderBalanceChecker;
import org.hyperledger.besu.sila.sil.transactions.layered.SparseTransactions;
import org.hyperledger.besu.sila.sil.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.sila.sil.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.sila.sil.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.feemarket.FeeMarket;

import java.time.Clock;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolFactory {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolFactory.class);

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncState syncState,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final SilProtocolConfiguration silProtocolConfiguration,
      final BlobCache blobCache,
      final MiningConfiguration miningConfiguration) {

    final TransactionPoolMetrics metrics = new TransactionPoolMetrics(metricsSystem);

    final PeerTransactionTracker transactionTracker =
        new PeerTransactionTracker(
            transactionPoolConfiguration, silContext.getEthPeers(), silContext.getScheduler());
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(
            transactionTracker, silProtocolConfiguration.getMaxTransactionsMessageSize());

    final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender =
        new NewPooledTransactionHashesMessageSender(transactionTracker);

    return createTransactionPool(
        protocolSchedule,
        protocolContext,
        silContext,
        clock,
        metrics,
        syncState,
        transactionPoolConfiguration,
        transactionTracker,
        transactionsMessageSender,
        newPooledTransactionHashesMessageSender,
        blobCache,
        miningConfiguration,
        silProtocolConfiguration);
  }

  static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final Clock clock,
      final TransactionPoolMetrics metrics,
      final SyncState syncState,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender,
      final BlobCache blobCache,
      final MiningConfiguration miningConfiguration,
      final SilProtocolConfiguration silProtocolConfiguration) {

    final TransactionPool transactionPool =
        new TransactionPool(
            () ->
                createPendingTransactions(
                    protocolSchedule,
                    protocolContext,
                    silContext.getScheduler(),
                    clock,
                    metrics,
                    transactionPoolConfiguration,
                    blobCache,
                    miningConfiguration),
            protocolSchedule,
            protocolContext,
            new TransactionBroadcaster(
                silContext,
                transactionTracker,
                transactionsMessageSender,
                newPooledTransactionHashesMessageSender),
            silContext,
            metrics,
            transactionPoolConfiguration,
            blobCache);

    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            silContext.getScheduler(),
            new TransactionsMessageProcessor(
                transactionTracker,
                transactionPool,
                metrics,
                silProtocolConfiguration.getMaxTransactionsPerMessage()),
            transactionPoolConfiguration.getUnstable().getTxMessageKeepAliveSeconds(),
            silProtocolConfiguration.getMaxMessageSize());

    final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler =
        new NewPooledTransactionHashesMessageHandler(
            silContext.getScheduler(),
            new NewPooledTransactionHashesMessageProcessor(
                transactionTracker,
                transactionPool,
                transactionPoolConfiguration,
                silContext,
                metrics,
                silProtocolConfiguration.getMaxTransactionsMessageSize()),
            transactionPoolConfiguration.getUnstable().getTxMessageKeepAliveSeconds());

    subscribeTransactionHandlers(
        protocolContext,
        silContext,
        transactionTracker,
        transactionPool,
        transactionsMessageHandler,
        pooledTransactionsMessageHandler);

    if (syncState.isInitialSyncPhaseDone()) {
      LOG.info("Enabling transaction pool");
      pooledTransactionsMessageHandler.setEnabled();
      transactionsMessageHandler.setEnabled();
      transactionPool.setEnabled();
    } else {
      LOG.info("Transaction pool disabled while initial sync in progress");
    }

    syncState.subscribeCompletionReached(
        new BesuEvents.InitialSyncCompletionListener() {
          @Override
          public void onInitialSyncCompleted() {
            LOG.info("Enabling transaction handling following initial sync");
            enableTransactionHandling(
                transactionTracker,
                transactionPool,
                transactionsMessageHandler,
                pooledTransactionsMessageHandler);
          }

          @Override
          public void onInitialSyncRestart() {
            LOG.info("Disabling transaction handling during re-sync");
            disableTransactionHandling(
                transactionPool, transactionsMessageHandler, pooledTransactionsMessageHandler);
          }
        });

    syncState.subscribeInSync(
        isInSync -> {
          if (isInSync != transactionPool.isEnabled()) {
            if (isInSync && syncState.isInitialSyncPhaseDone()) {
              LOG.info("Node is in sync, enabling transaction handling");
              enableTransactionHandling(
                  transactionTracker,
                  transactionPool,
                  transactionsMessageHandler,
                  pooledTransactionsMessageHandler);
            } else {
              if (transactionPool.isEnabled()) {
                LOG.info("Node out of sync, disabling transaction handling");
                disableTransactionHandling(
                    transactionPool, transactionsMessageHandler, pooledTransactionsMessageHandler);
              }
            }
          }
        });

    return transactionPool;
  }

  private static void enableTransactionHandling(
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final TransactionsMessageHandler transactionsMessageHandler,
      final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler) {
    transactionTracker.reset();
    transactionPool.setEnabled();
    transactionsMessageHandler.setEnabled();
    pooledTransactionsMessageHandler.setEnabled();
  }

  private static void disableTransactionHandling(
      final TransactionPool transactionPool,
      final TransactionsMessageHandler transactionsMessageHandler,
      final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler) {
    transactionPool.setDisabled();
    transactionsMessageHandler.setDisabled();
    pooledTransactionsMessageHandler.setDisabled();
  }

  private static void subscribeTransactionHandlers(
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final TransactionsMessageHandler transactionsMessageHandler,
      final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler) {
    silContext.getEthPeers().subscribeConnect(transactionTracker);
    silContext.getEthPeers().subscribeDisconnect(transactionTracker);
    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    protocolContext.getBlockchain().observeBlockAdded(transactionTracker);
    silContext
        .getEthMessages()
        .subscribe(SilProtocolMessages.TRANSACTIONS, transactionsMessageHandler);
    silContext
        .getEthMessages()
        .subscribe(
            SilProtocolMessages.NEW_POOLED_TRANSACTION_HASHES, pooledTransactionsMessageHandler);
  }

  private static PendingTransactions createPendingTransactions(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilScheduler silScheduler,
      final Clock clock,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final BlobCache blobCache,
      final MiningConfiguration miningConfiguration) {

    boolean isFeeMarketImplementBaseFee =
        protocolSchedule.anyMatch(
            scheduledSpec -> scheduledSpec.spec().getFeeMarket().implementsBaseFee());

    if (transactionPoolConfiguration.getTxPoolImplementation().equals(LAYERED)) {
      return createLayeredPendingTransactions(
          protocolSchedule,
          protocolContext,
          silScheduler,
          metrics,
          transactionPoolConfiguration,
          isFeeMarketImplementBaseFee,
          blobCache,
          miningConfiguration);
    } else {
      return createPendingTransactionSorter(
          protocolContext,
          clock,
          metrics.getMetricsSystem(),
          transactionPoolConfiguration,
          isFeeMarketImplementBaseFee);
    }
  }

  private static AbstractPendingTransactionsSorter createPendingTransactionSorter(
      final ProtocolContext protocolContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final boolean isFeeMarketImplementBaseFee) {
    if (isFeeMarketImplementBaseFee) {
      return new BaseFeePendingTransactionsSorter(
          transactionPoolConfiguration,
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader);
    } else {
      return new GasPricePendingTransactionsSorter(
          transactionPoolConfiguration,
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader);
    }
  }

  private static PendingTransactions createLayeredPendingTransactions(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilScheduler silScheduler,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final boolean isFeeMarketImplementBaseFee,
      final BlobCache blobCache,
      final MiningConfiguration miningConfiguration) {

    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(
            transactionPoolConfiguration.getPriceBump(),
            transactionPoolConfiguration.getBlobPriceBump());

    final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
        (t1, t2) ->
            transactionReplacementHandler.shouldReplace(
                t1, t2, protocolContext.getBlockchain().getChainHeadHeader());

    final SenderBalanceChecker senderBalanceChecker =
        SenderBalanceChecker.create(
            protocolSchedule, protocolContext, transactionPoolConfiguration);

    final EndLayer endLayer = new EndLayer(metrics);

    final SparseTransactions sparseTransactions =
        new SparseTransactions(
            transactionPoolConfiguration,
            silScheduler,
            endLayer,
            metrics,
            transactionReplacementTester,
            blobCache);

    final ReadyTransactions readyTransactions =
        new ReadyTransactions(
            transactionPoolConfiguration,
            silScheduler,
            sparseTransactions,
            metrics,
            transactionReplacementTester,
            blobCache);

    final AbstractPrioritizedTransactions pendingTransactionsSorter;
    if (isFeeMarketImplementBaseFee) {
      final FeeMarket feeMarket =
          protocolSchedule
              .getByBlockHeader(protocolContext.getBlockchain().getChainHeadHeader())
              .getFeeMarket();

      pendingTransactionsSorter =
          new BaseFeePrioritizedTransactions(
              transactionPoolConfiguration,
              protocolContext.getBlockchain()::getChainHeadHeader,
              silScheduler,
              readyTransactions,
              metrics,
              transactionReplacementTester,
              feeMarket,
              blobCache,
              miningConfiguration,
              senderBalanceChecker);
    } else {
      pendingTransactionsSorter =
          new GasPricePrioritizedTransactions(
              transactionPoolConfiguration,
              silScheduler,
              readyTransactions,
              metrics,
              transactionReplacementTester,
              blobCache,
              miningConfiguration,
              senderBalanceChecker);
    }

    return new LayeredPendingTransactions(
        protocolContext, transactionPoolConfiguration, pendingTransactionsSorter, silScheduler);
  }
}
