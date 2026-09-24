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
package org.hyperledger.besu.ethstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethstats.request.SilStatsRequest;
import org.hyperledger.besu.ethstats.util.ImmutableSilStatsConnectOptions;
import org.hyperledger.besu.ethstats.util.SilStatsConnectOptions;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.sila.api.query.BlockWithMetadata;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.api.query.TransactionWithMetadata;
import org.hyperledger.besu.sila.blockcreation.MiningCoordinator;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.p2p.network.P2PNetwork;
import org.hyperledger.besu.sila.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SilStatsServiceTest {

  @Mock private Vertx vertx;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private SilProtocolManager silProtocolManager;
  @Mock private TransactionPool transactionPool;
  @Mock private MiningCoordinator miningCoordinator;
  @Mock private SyncState syncState;
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private P2PNetwork p2PNetwork;
  @Mock private SilContext silContext;
  @Mock private SilScheduler silScheduler;
  @Mock private WebSocketClient webSocketClient;
  @Mock private WebSocket webSocket;

  final SilStatsConnectOptions silStatsConnectOptions =
      ImmutableSilStatsConnectOptions.builder()
          .nodeName("besu-node")
          .secret("secret")
          .host("127.0.0.1")
          .port(1111)
          .contact("contact@test.net")
          .silStatsReportInterval(5)
          .build();

  final EnodeURLImpl node =
      EnodeURLImpl.builder()
          .nodeId(
              "50203c6bfca6874370e71aecc8958529fd723feb05013dc1abca8fc1fff845c5259faba05852e9dfe5ce172a7d6e7c2a3a5eaa8b541c8af15ea5518bbff5f2fa")
          .useDefaultPorts()
          .ipAddress("127.0.0.1")
          .listeningPort(30304)
          .build();

  private SilStatsService silStatsService;

  @BeforeEach
  public void initMocks() {
    when(silProtocolManager.silContext()).thenReturn(silContext);
    when(silContext.getScheduler()).thenReturn(silScheduler);
    when(vertx.createWebSocketClient(any(WebSocketClientOptions.class)))
        .thenReturn(webSocketClient);
    when(webSocketClient.connect(any(WebSocketConnectOptions.class)))
        .thenReturn(Future.succeededFuture(webSocket));
    when(webSocket.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());
    when(genesisConfigOptions.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
    when(silProtocolManager.getSupportedCapabilities())
        .thenReturn(List.of(Capability.create("sil64", 1)));
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
  }

  @Test
  public void shouldRetryWhenLocalEnodeNotAvailable() throws Exception {
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenThrow(new NoSuchElementException());
    silStatsService.start();
    verify(silScheduler, times(1)).scheduleFutureTask(any(Runnable.class), any(Duration.class));
  }

  @Test
  public void shouldSendHelloMessage() {
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));

    final ArgumentCaptor<String> helloMessageCaptor = ArgumentCaptor.forClass(String.class);
    verify(webSocket, times(1)).writeTextMessage(helloMessageCaptor.capture());

    assertThat(helloMessageCaptor.getValue().contains(SilStatsRequest.Type.HELLO.getValue()))
        .isTrue();
  }

  @Test
  public void shouldRetryIfSendHelloMessageFailed() {

    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));
    when(webSocket.writeTextMessage(anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("test failure")));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));
    verify(webSocket, times(1)).writeTextMessage(anyString());

    verify(silScheduler, times(1)).scheduleFutureTask(any(Runnable.class), any(Duration.class));
  }

  @Test
  public void shouldSendFullReportIfHelloMessageSucceeded() {
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));

    final ArgumentCaptor<Handler<String>> textMessageHandlerCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(webSocket, times(1)).textMessageHandler(textMessageHandlerCaptor.capture());

    textMessageHandlerCaptor.getValue().handle("{\"emit\":[\"ready\"]}");

    verify(silScheduler, times(1)).scheduleFutureTaskWithFixedDelay(any(), any(), any());
  }

  @Test
  public void shouldSendBlockMessage() throws Exception {
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);

    final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
    final Block testBlock = blockDataGenerator.block();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        new BlockWithMetadata<>(
            testBlock.getHeader(),
            List.of(),
            List.of(),
            testBlock.getHeader().getDifficulty(),
            testBlock.getSize(),
            Optional.empty());

    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));
    when(blockchainQueries.latestBlock()).thenReturn(Optional.of(blockWithMetadata));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));

    // send block message
    silStatsService.sendBlockReport();
    final ArgumentCaptor<String> messagesCaptor = ArgumentCaptor.forClass(String.class);
    verify(webSocket, times(2)).writeTextMessage(messagesCaptor.capture());

    final List<String> sentMessages = messagesCaptor.getAllValues();
    assertThat(sentMessages.get(0)).contains("hello");

    final String blockMessage = sentMessages.get(1);
    assertThat(blockMessage).contains("\"block\"");

    // verify block message
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    final var jsonNode = objectMapper.readTree(blockMessage);
    final var blockReportData = jsonNode.get("emit").get(1);
    final var blockDataNode = blockReportData.get("block");

    final var expectedBlockResult = new BlockResultFactory().transactionComplete(blockWithMetadata);
    final JsonNode expectedBlockResultNode = objectMapper.valueToTree(expectedBlockResult);

    assertThat(blockDataNode).isEqualTo(expectedBlockResultNode);
  }

  @Test
  public void shouldThrowWhenSendBlockReportCalledBeforeConnect() {
    assertThatThrownBy(() -> silStatsService.sendBlockReport())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebSocket connection is required but is not available.");
  }

  @Test
  public void shouldThrowWhenSendBlockReportCalledBeforeEnodeInitialized() throws Exception {
    setPrivateField("webSocket", webSocket);

    assertThatThrownBy(() -> silStatsService.sendBlockReport())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Local enode URL has not been initialized yet.");
  }

  private void setPrivateField(final String fieldName, final Object value) throws Exception {
    final Field field = SilStatsService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(silStatsService, value);
  }

  @Test
  public void shouldUseCustomReportInterval() {
    // Test with custom 10-second interval
    final SilStatsConnectOptions customIntervalOptions =
        ImmutableSilStatsConnectOptions.builder()
            .nodeName("besu-node")
            .secret("secret")
            .host("127.0.0.1")
            .port(1111)
            .contact("contact@test.net")
            .silStatsReportInterval(10) // Custom 10-second interval
            .build();

    silStatsService =
        new SilStatsService(
            customIntervalOptions,
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));

    final ArgumentCaptor<Handler<String>> textMessageHandlerCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(webSocket, times(1)).textMessageHandler(textMessageHandlerCaptor.capture());

    textMessageHandlerCaptor.getValue().handle("{\"emit\":[\"ready\"]}");

    // Verify that scheduleFutureTaskWithFixedDelay is called with the custom 10-second interval
    final ArgumentCaptor<Duration> initialDelayCaptor = ArgumentCaptor.forClass(Duration.class);
    final ArgumentCaptor<Duration> intervalCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(silScheduler, times(1))
        .scheduleFutureTaskWithFixedDelay(
            any(Runnable.class), initialDelayCaptor.capture(), intervalCaptor.capture());

    // Verify the interval is 10 seconds (custom value)
    assertThat(intervalCaptor.getValue()).isEqualTo(Duration.ofSeconds(10));
    // Verify initial delay is 0 seconds
    assertThat(initialDelayCaptor.getValue()).isEqualTo(Duration.ofSeconds(0));
  }

  @Test
  public void shouldUseDefaultReportIntervalWhenNotSpecified() {
    // Test with default 5-second interval (backward compatibility)
    silStatsService =
        new SilStatsService(
            silStatsConnectOptions, // This uses the default 5-second interval
            blockchainQueries,
            silProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    silStatsService.start();

    verify(webSocketClient, times(1)).connect(any(WebSocketConnectOptions.class));

    final ArgumentCaptor<Handler<String>> textMessageHandlerCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(webSocket, times(1)).textMessageHandler(textMessageHandlerCaptor.capture());

    textMessageHandlerCaptor.getValue().handle("{\"emit\":[\"ready\"]}");

    // Verify that scheduleFutureTaskWithFixedDelay is called with the default 5-second interval
    final ArgumentCaptor<Duration> initialDelayCaptor = ArgumentCaptor.forClass(Duration.class);
    final ArgumentCaptor<Duration> intervalCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(silScheduler, times(1))
        .scheduleFutureTaskWithFixedDelay(
            any(Runnable.class), initialDelayCaptor.capture(), intervalCaptor.capture());

    // Verify the interval is 5 seconds (default value)
    assertThat(intervalCaptor.getValue()).isEqualTo(Duration.ofSeconds(5));
    // Verify initial delay is 0 seconds
    assertThat(initialDelayCaptor.getValue()).isEqualTo(Duration.ofSeconds(0));
  }

  @Test
  public void shouldGenerateNonEmptyHistoryBlockRange() {
    final List<Long> blocks = SilStatsService.buildHistoryBlockList(100L);

    assertThat(blocks).hasSize(51);
    assertThat(blocks).startsWith(50L);
    assertThat(blocks).endsWith(100L);
  }

  @Test
  public void shouldHandleChainHeadSmallerThanHistoryRange() {
    final List<Long> blocks = SilStatsService.buildHistoryBlockList(30L);

    assertThat(blocks).hasSize(31);
    assertThat(blocks).startsWith(0L);
    assertThat(blocks).endsWith(30L);
  }
}
