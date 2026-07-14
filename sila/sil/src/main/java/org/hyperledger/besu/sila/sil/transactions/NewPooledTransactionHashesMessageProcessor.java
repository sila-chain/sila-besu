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

import static java.time.Instant.now;

import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.task.BufferedGetPooledTransactionsFromPeerFetcher;
import org.hyperledger.besu.sila.sil.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.rlp.RLPException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewPooledTransactionHashesMessageProcessor {

  private static final Logger LOG =
      LoggerFactory.getLogger(NewPooledTransactionHashesMessageProcessor.class);

  static final String METRIC_LABEL = "new_pooled_transaction_hashes";

  private final ConcurrentHashMap<SilPeer, ScheduledFuture<?>> scheduledTasks;

  private final PeerTransactionTracker transactionTracker;
  private final TransactionPool transactionPool;
  private final TransactionPoolConfiguration transactionPoolConfiguration;
  private final SilContext silContext;
  private final TransactionPoolMetrics metrics;
  private final int maxTransactionsMessageSize;

  public NewPooledTransactionHashesMessageProcessor(
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final SilContext silContext,
      final TransactionPoolMetrics metrics,
      final int maxTransactionsMessageSize) {
    this.transactionTracker = transactionTracker;
    this.transactionPool = transactionPool;
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    this.silContext = silContext;
    this.metrics = metrics;
    metrics.initExpiredMessagesCounter(METRIC_LABEL);
    this.scheduledTasks = new ConcurrentHashMap<>();
    this.maxTransactionsMessageSize = maxTransactionsMessageSize;
  }

  void processNewPooledTransactionHashesMessage(
      final SilPeer peer,
      final NewPooledTransactionHashesMessage transactionsMessage,
      final Instant queueAt,
      final Duration keepAlive) {
    // Check if message is not expired.
    final var latency = Duration.between(queueAt, now());
    if (latency.compareTo(keepAlive) < 0) {
      processNewPooledTransactionHashesMessage(peer, transactionsMessage);
    } else {
      LOG.atTrace()
          .setMessage(
              "Ignoring expired transactions message: peer={}, latency={}, queuedAt={}, keepAlive={}, announcements={}")
          .addArgument(peer)
          .addArgument(latency)
          .addArgument(queueAt)
          .addArgument(keepAlive)
          .addArgument(transactionsMessage::pendingTransactionAnnouncements)
          .log();
      metrics.incrementExpiredMessages(METRIC_LABEL);
    }
  }

  private void processNewPooledTransactionHashesMessage(
      final SilPeer peer, final NewPooledTransactionHashesMessage transactionsMessage) {
    try {
      final List<TransactionAnnouncement> incomingAnnouncements =
          transactionsMessage.pendingTransactionAnnouncements();

      final var freshAnnouncements =
          transactionTracker.receivedAnnouncements(peer, incomingAnnouncements);

      metrics.incrementAlreadySeenTransactions(
          METRIC_LABEL, incomingAnnouncements.size() - freshAnnouncements.size());

      LOG.atTrace()
          .setMessage(
              "Received pooled transaction hashes message: peer={}, incoming hashes={}, fresh hashes={}")
          .addArgument(peer)
          .addArgument(incomingAnnouncements)
          .addArgument(freshAnnouncements)
          .log();

      scheduledTasks.computeIfAbsent(
          peer,
          silPeer ->
              silContext
                  .getScheduler()
                  .scheduleFutureTaskWithFixedDelay(
                      new FetcherCreatorTask(
                          peer,
                          new BufferedGetPooledTransactionsFromPeerFetcher(
                              silContext,
                              peer,
                              transactionPool,
                              transactionTracker,
                              maxTransactionsMessageSize)),
                      transactionPoolConfiguration
                          .getUnstable()
                          .getSil65TrxAnnouncedBufferingPeriod(),
                      transactionPoolConfiguration
                          .getUnstable()
                          .getSil65TrxAnnouncedBufferingPeriod()));
    } catch (final RLPException ex) {
      if (peer != null) {
        LOG.debug(
            "Malformed pooled transaction hashes message received (BREACH_OF_PROTOCOL), disconnecting: {}",
            peer,
            ex);
        LOG.trace("Message data: {}", transactionsMessage.getData());
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
      }
    }
  }

  class FetcherCreatorTask implements Runnable {
    final SilPeer peer;
    final BufferedGetPooledTransactionsFromPeerFetcher fetcher;

    public FetcherCreatorTask(
        final SilPeer peer, final BufferedGetPooledTransactionsFromPeerFetcher fetcher) {
      this.peer = peer;
      this.fetcher = fetcher;
    }

    @Override
    public void run() {
      if (peer != null) {
        if (peer.isDisconnected()) {
          scheduledTasks.remove(peer).cancel(true);
        } else if (peer.hasAvailableRequestCapacity()) {
          silContext.getScheduler().scheduleServiceTask(fetcher::requestTransactions);
        }
      }
    }
  }
}
