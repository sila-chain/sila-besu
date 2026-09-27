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
package org.hyperledger.besu.silstats;

import static com.google.common.collect.Streams.stream;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.EMIT_FIELD;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.MAPPER;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.BLOCK;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.HELLO;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.HISTORY;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.LATENCY;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.NODE_PING;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.NODE_PONG;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.PENDING;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.READY;
import static org.hyperledger.besu.silstats.request.SilStatsRequest.Type.STATS;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.blockcreation.MiningCoordinator;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.p2p.network.P2PNetwork;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.silstats.authentication.ImmutableAuthenticationData;
import org.hyperledger.besu.silstats.authentication.ImmutableNodeInfo;
import org.hyperledger.besu.silstats.authentication.NodeInfo;
import org.hyperledger.besu.silstats.report.ImmutableBlockReport;
import org.hyperledger.besu.silstats.report.ImmutableHistoryReport;
import org.hyperledger.besu.silstats.report.ImmutableLatencyReport;
import org.hyperledger.besu.silstats.report.ImmutableNodeStatsReport;
import org.hyperledger.besu.silstats.report.ImmutablePendingTransactionsReport;
import org.hyperledger.besu.silstats.report.ImmutablePingReport;
import org.hyperledger.besu.silstats.report.NodeStatsReport;
import org.hyperledger.besu.silstats.report.PendingTransactionsReport;
import org.hyperledger.besu.silstats.request.SilStatsRequest;
import org.hyperledger.besu.silstats.util.PrimusHeartBeatsHelper;
import org.hyperledger.besu.silstats.util.SilStatsConnectOptions;
import org.hyperledger.besu.util.platform.PlatformDetector;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.net.PemTrustOptions;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class describes the behaviour of the SilStatsService. This class is used to report pending
 * transactions, blocks, and several node-related information to an silstats server.
 */
public class SilStatsService {

  private static final Logger LOG = LoggerFactory.getLogger(SilStatsService.class);

  private static final int HISTORY_RANGE = 50;

  private final AtomicBoolean retryInProgress = new AtomicBoolean(false);

  private final SilStatsConnectOptions silStatsConnectOptions;
  private final SilProtocolManager protocolManager;
  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final SyncState syncState;
  private final Vertx vertx;
  private final String clientVersion;
  private final GenesisConfigOptions genesisConfigOptions;
  private final P2PNetwork p2PNetwork;
  private final BlockchainQueries blockchainQueries;
  private final BlockResultFactory blockResultFactory;
  private final WebSocketClientOptions webSocketClientOptions;
  private final WebSocketConnectOptions webSocketConnectOptions;

  private @Nullable ScheduledFuture<?> reportScheduler;
  private @Nullable WebSocket webSocket;
  private @Nullable EnodeURL enodeURL;
  private long pingTimestamp;

  /**
   * Instantiates a new SilStatsService.
   *
   * @param silStatsConnectOptions the silstats options
   * @param blockchainQueries the blockchain queries
   * @param protocolManager the protocol manager
   * @param transactionPool the transaction pool
   * @param miningCoordinator the mining coordinator
   * @param syncState the SyncState
   * @param vertx the vertx instance
   * @param clientVersion the client version
   * @param genesisConfigOptions the genesis config options
   * @param p2PNetwork the p2p network
   */
  public SilStatsService(
      final SilStatsConnectOptions silStatsConnectOptions,
      final BlockchainQueries blockchainQueries,
      final SilProtocolManager protocolManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final SyncState syncState,
      final Vertx vertx,
      final String clientVersion,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork p2PNetwork) {
    this.silStatsConnectOptions = silStatsConnectOptions;
    this.blockchainQueries = blockchainQueries;
    this.protocolManager = protocolManager;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.vertx = vertx;
    this.syncState = syncState;
    this.clientVersion = clientVersion;
    this.genesisConfigOptions = genesisConfigOptions;
    this.p2PNetwork = p2PNetwork;
    this.blockResultFactory = new BlockResultFactory();
    this.webSocketClientOptions = buildWebSocketClientOptions(silStatsConnectOptions);
    this.webSocketConnectOptions = buildWebSocketConnectOptions(silStatsConnectOptions);
  }

  private static WebSocketClientOptions buildWebSocketClientOptions(
      final SilStatsConnectOptions silStatsConnectOptions) {
    final WebSocketClientOptions options = new WebSocketClientOptions();
    if (silStatsConnectOptions.getCaCert() != null) {
      options.setTrustOptions(
          new PemTrustOptions().addCertPath(silStatsConnectOptions.getCaCert().toString()));
    }
    return options;
  }

  private static WebSocketConnectOptions buildWebSocketConnectOptions(
      final SilStatsConnectOptions silStatsConnectOptions) {
    // if user specified scheme is null, default ssl to true, otherwise set ssl to true for wss
    // scheme.
    final boolean isSSL =
        silStatsConnectOptions.getScheme() == null
            || silStatsConnectOptions.getScheme().equalsIgnoreCase("wss");
    return new WebSocketConnectOptions()
        .setURI("/api")
        .setSsl(isSSL)
        .setHost(silStatsConnectOptions.getHost())
        .setPort(getWsPort(silStatsConnectOptions, isSSL));
  }

  private static int getWsPort(
      final SilStatsConnectOptions silStatsConnectOptions, final boolean isSSL) {
    if (silStatsConnectOptions.getPort() >= 0) {
      return silStatsConnectOptions.getPort();
    }
    return isSSL ? 443 : 80;
  }

  /** Start. */
  public void start() {
    LOG.debug("Connecting to SilStats: {}", getSilStatsURI());
    try {
      enodeURL = p2PNetwork.getLocalEnode().orElseThrow();
      vertx
          .createWebSocketClient(webSocketClientOptions)
          .connect(webSocketConnectOptions)
          .onComplete(
              event -> {
                if (event.succeeded()) {
                  webSocket = event.result();

                  // reconnect if we lose the connection or if an error occurs
                  webSocket.exceptionHandler(ex -> retryConnect());
                  webSocket.closeHandler(handler -> retryConnect());

                  // listen to the messages from the silstats server in order to validate the
                  // connection
                  webSocket.textMessageHandler(
                      ack -> {
                        SilStatsRequest silStatsRequest = SilStatsRequest.fromResponse(ack);
                        if (silStatsRequest.getType().equals(READY)) {
                          LOG.info("Connected to silstats server");

                          // listen to messages from the silstats server
                          startListeningSilStatsServer();
                          // send a full report after the connection
                          sendFullReport();
                        } else {
                          LOG.error("Failed to login to silstats server {}", ack);
                        }
                      });

                  retryInProgress.set(false);
                  // sending a hello to initiate the connection using the secret
                  sendHello();
                } else {
                  LOG.error(
                      "Failed to reach the silstats server due to: {}", event.cause().getMessage());
                  retryInProgress.set(false);
                  retryConnect();
                }
              });

    } catch (Exception e) {
      retryConnect();
    }
  }

  private String getSilStatsURI() {
    return String.format(
        "%s://%s:%s",
        webSocketConnectOptions.isSsl() ? "wss" : "ws",
        silStatsConnectOptions.getHost(),
        getWsPort(silStatsConnectOptions, webSocketConnectOptions.isSsl()));
  }

  /**
   * Switch from ssl to non-ssl and vice-versa if user specified scheme is null. Sets port to 443 or
   * 80 if not specified.
   */
  private void updateSSLProtocol() {
    if (silStatsConnectOptions.getScheme() == null) {
      final boolean updatedSSL = !webSocketConnectOptions.isSsl();
      webSocketConnectOptions.setSsl(updatedSSL);
    }
    webSocketConnectOptions.setPort(
        getWsPort(silStatsConnectOptions, webSocketConnectOptions.isSsl()));
  }

  /** Ends the current web socket connection, observers and schedulers */
  public void stop() {
    if (webSocket != null && !webSocket.isClosed()) {
      webSocket.close();
    }
    if (reportScheduler != null) {
      reportScheduler.cancel(true);
    }
  }

  /** Ends the current connection and restart a new one. */
  private void retryConnect() {
    if (retryInProgress.getAndSet(true) == FALSE) {
      stop();
      updateSSLProtocol(); // switch from ssl:true to ssl:false and vice-versa
      LOG.info("Attempting to reconnect to silstats server in approximately 10 seconds.");
      protocolManager
          .silContext()
          .getScheduler()
          .scheduleFutureTask(this::start, Duration.ofSeconds(10));
    }
  }

  /** Sends a hello request to the silstats server in order to log in. */
  private void sendHello() {
    try {
      final EnodeURL localEnodeURL = requireEnodeURL();
      final WebSocket activeWebSocket = requireWebSocket();
      final Optional<Integer> port = localEnodeURL.getListeningPort();
      final Optional<BigInteger> chainId = genesisConfigOptions.getChainId();
      if (port.isPresent() && chainId.isPresent()) {
        final String os = PlatformDetector.getOSType();
        final String arch = PlatformDetector.getArch();

        final NodeInfo nodeInfo =
            ImmutableNodeInfo.of(
                silStatsConnectOptions.getNodeName(),
                clientVersion,
                String.valueOf(port.get()),
                chainId.get().toString(),
                protocolManager.getSupportedCapabilities().toString(),
                "No",
                os,
                arch,
                "0.1.1",
                true,
                silStatsConnectOptions.getContact());

        final SilStatsRequest hello =
            new SilStatsRequest(
                HELLO,
                ImmutableAuthenticationData.of(
                    localEnodeURL.getNodeId().toHexString(),
                    nodeInfo,
                    silStatsConnectOptions.getSecret()));
        sendMessage(
            activeWebSocket,
            hello,
            isSucceeded -> {
              if (!isSucceeded) {
                retryConnect();
              }
            });
      } else {
        throw new NoSuchElementException();
      }
    } catch (NoSuchElementException e) {
      LOG.error("Failed to find required parameters for silstats request : {}", e.getMessage());
      retryConnect();
    }
  }

  /** Sends a full report to the silstats server */
  private void sendFullReport() {
    reportScheduler =
        protocolManager
            .silContext()
            .getScheduler()
            .scheduleFutureTaskWithFixedDelay(
                () -> {
                  sendPing();
                  sendBlockReport();
                  sendPendingTransactionReport();
                  sendNodeStatsReport();
                },
                Duration.ofSeconds(0),
                Duration.ofSeconds(silStatsConnectOptions.getSilStatsReportInterval()));
  }

  /** Sends a ping request to the silstats server */
  private void sendPing() {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();

    // we store the timestamp when we sent the ping
    pingTimestamp = System.currentTimeMillis();

    sendMessage(
        activeWebSocket,
        new SilStatsRequest(
            NODE_PING,
            ImmutablePingReport.of(
                localEnodeURL.getNodeId().toHexString(), String.valueOf(pingTimestamp))));
  }

  /** Sends a latency report to the silstats server */
  private void sendLatencyReport() {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();

    sendMessage(
        activeWebSocket,
        new SilStatsRequest(
            LATENCY,
            ImmutableLatencyReport.of(
                localEnodeURL.getNodeId().toHexString(),
                String.valueOf(System.currentTimeMillis() - pingTimestamp))));
  }

  /** Sends a block report concerning the last block */
  @VisibleForTesting
  protected void sendBlockReport() {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();

    blockchainQueries
        .latestBlock()
        .map(tx -> blockResultFactory.transactionComplete(tx, false))
        .ifPresent(
            blockResult ->
                sendMessage(
                    activeWebSocket,
                    new SilStatsRequest(
                        BLOCK,
                        ImmutableBlockReport.of(
                            localEnodeURL.getNodeId().toHexString(), blockResult))));
  }

  /** Sends a report concerning a set of blocks (range, list of blocks) */
  private void sendHistoryReport(final List<Long> blocks) {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();
    final List<BlockResult> blockResults = new ArrayList<>();

    blocks.forEach(
        blockNumber ->
            blockchainQueries
                .blockByNumber(blockNumber)
                .map(tx -> blockResultFactory.transactionComplete(tx, false))
                .ifPresent(blockResults::add));

    if (!blockResults.isEmpty()) {
      sendMessage(
          activeWebSocket,
          new SilStatsRequest(
              HISTORY,
              ImmutableHistoryReport.of(localEnodeURL.getNodeId().toHexString(), blockResults)));
    }
  }

  /** Sends the number of pending transactions in the pool */
  private void sendPendingTransactionReport() {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();
    final int pendingTransactionsNumber = transactionPool.count();

    final PendingTransactionsReport pendingTransactionsReport =
        ImmutablePendingTransactionsReport.builder()
            .id(localEnodeURL.getNodeId().toHexString())
            .stats(pendingTransactionsNumber)
            .build();

    sendMessage(activeWebSocket, new SilStatsRequest(PENDING, pendingTransactionsReport));
  }

  /** Sends information about the node (is mining, is syncing, etc.) */
  private void sendNodeStatsReport() {
    final WebSocket activeWebSocket = requireWebSocket();
    final EnodeURL localEnodeURL = requireEnodeURL();
    final boolean isMiningEnabled = miningCoordinator.isMining();
    final boolean isSyncing = syncState.isInSync();
    final long gasPrice = suggestGasPrice(blockchainQueries.getBlockchain().getChainHeadBlock());
    // safe to cast to int since it isn't realistic to have more than max int peers
    final int peersNumber =
        (int) protocolManager.silContext().getEthPeers().streamAvailablePeers().count();

    final NodeStatsReport nodeStatsReport =
        ImmutableNodeStatsReport.builder()
            .id(localEnodeURL.getNodeId().toHexString())
            .stats(true, isMiningEnabled, 0L, peersNumber, gasPrice, isSyncing, 100)
            .build();
    sendMessage(activeWebSocket, new SilStatsRequest(STATS, nodeStatsReport));
  }

  private WebSocket requireWebSocket() {
    if (webSocket == null) {
      throw new IllegalStateException("WebSocket connection is required but is not available.");
    }
    return webSocket;
  }

  private EnodeURL requireEnodeURL() {
    if (enodeURL == null) {
      throw new IllegalStateException("Local enode URL has not been initialized yet.");
    }
    return enodeURL;
  }

  private void sendMessage(
      final WebSocket webSocket,
      final SilStatsRequest message,
      final Consumer<Boolean> handlerResult) {
    try {
      LOG.trace("Send silstats request {}", message.generateCommand());
      webSocket
          .writeTextMessage(message.generateCommand())
          .onComplete(
              handler -> {
                if (!handler.succeeded()) {
                  LOG.error("Failed to send {} silstats request", message.getType());
                  handlerResult.accept(FALSE);
                } else {
                  handlerResult.accept(TRUE);
                }
              });
    } catch (Exception e) {
      LOG.error(
          "Failed to send {} silstats request with error {}", message.getType(), e.getMessage());
      handlerResult.accept(FALSE);
    }
  }

  private void sendMessage(final WebSocket webSocket, final SilStatsRequest message) {
    sendMessage(webSocket, message, __ -> {});
  }

  private void startListeningSilStatsServer() {
    final WebSocket activeWebSocket = requireWebSocket();

    activeWebSocket.textMessageHandler(
        message -> {
          try {
            if (PrimusHeartBeatsHelper.isHeartBeatsRequest(message)) {
              PrimusHeartBeatsHelper.sendHeartBeatsResponse(activeWebSocket);
            } else {
              final JsonNode jsonNode = MAPPER.readTree(message);
              final JsonNode parameters = jsonNode.get(EMIT_FIELD);
              if (parameters.isArray()) {
                final SilStatsRequest.Type type =
                    SilStatsRequest.Type.fromValue(parameters.get(0).asText());
                if (type.equals(NODE_PONG)) {
                  sendLatencyReport();

                } else if (type.equals(HISTORY)) {
                  List<Long> list =
                      stream(parameters.get(1).withArray("list").elements())
                          .map(JsonNode::asLong)
                          .collect(Collectors.toList());
                  //  if the server does not send a list, we recover the last 50 blocks
                  if (list.isEmpty()) {
                    list =
                        buildHistoryBlockList(
                            blockchainQueries.getBlockchain().getChainHeadBlockNumber());
                  }
                  sendHistoryReport(list);
                }
              }
            }
          } catch (Exception e) {
            LOG.debug("Ignore invalid request {}", message);
          }
        });
  }

  @VisibleForTesting
  static List<Long> buildHistoryBlockList(final long chainHeadBlockNumber) {
    final long start = Math.max(0, chainHeadBlockNumber - HISTORY_RANGE);
    return LongStream.rangeClosed(start, chainHeadBlockNumber).boxed().collect(Collectors.toList());
  }

  private long suggestGasPrice(final Block block) {
    // retrieves transactions from the last blocks and takes the lowest gas price. If no transaction
    // is present we return the minTransactionGasPrice of the mining coordinator
    return block.getBody().getTransactions().stream()
        .min(Comparator.comparing(t -> t.getEffectiveGasPrice(block.getHeader().getBaseFee())))
        .map(t -> t.getEffectiveGasPrice(block.getHeader().getBaseFee()))
        .filter(wei -> wei.getAsBigInteger().longValue() > 0)
        .orElse(miningCoordinator.getMinTransactionGasPrice())
        .getAsBigInteger()
        .longValue();
  }
}
