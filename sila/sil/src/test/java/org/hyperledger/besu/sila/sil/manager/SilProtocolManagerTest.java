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
package org.hyperledger.besu.sila.sil.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockBody;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.core.TransactionReceipt;
import org.hyperledger.besu.sila.forkid.ForkId;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.sil.ImmutableSilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.SilProtocolVersion;
import org.hyperledger.besu.sila.sil.core.Utils;
import org.hyperledger.besu.sila.sil.manager.MockPeerConnection.PeerSendHandler;
import org.hyperledger.besu.sila.sil.messages.BlockBodiesMessage;
import org.hyperledger.besu.sila.sil.messages.BlockHeadersMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.sila.sil.messages.GetReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.NewBlockMessage;
import org.hyperledger.besu.sila.sil.messages.ReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.messages.StatusMessage;
import org.hyperledger.besu.sila.sil.messages.TransactionsMessage;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.BlobCache;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolFactory;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// NullPointerExceptions on optional.get() will result in test failures anyway
@SuppressWarnings("OptionalGetWithoutIsPresent")
public final class SilProtocolManagerTest {

  private static Blockchain blockchain;
  private static TransactionPool transactionPool;
  private static ProtocolSchedule protocolSchedule;
  private static BlockDataGenerator gen;
  private static ProtocolContext protocolContext;
  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private static final ForkId forkId = new ForkId(Hash.ZERO.getBytes(), 0);

  @BeforeAll
  public static void setup() {
    gen = new BlockDataGenerator(0);
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    assertThat(blockchainSetupUtil.getMaxBlockNumber()).isGreaterThanOrEqualTo(20L);
  }

  @Test
  public void disconnectOnDecompressionFailure() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // Create a RawMessage with invalid compressed data that will throw FramingException
      final MessageData messageData =
          new RawMessage(SilProtocolMessages.GET_BLOCK_HEADERS, new byte[] {0x01, 0x02, 0x03});
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});
      silManager.processMessage(SilProtocol.SIL68, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
      assertThat(peer.getDisconnectReason())
          .contains(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    }
  }

  @Test
  public void handleMalformedRequestIdMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // this is a non-request id message, but we'll be processing it with sil66, make sure we
      // disconnect the peer gracefully
      final MessageData messageData = GetBlockHeadersMessage.create(1, 1, 0, false);
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});
      silManager.processMessage(SilProtocol.SIL68, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void disconnectOnUnsolicitedMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData =
          BlockHeadersMessage.create(Collections.singletonList(blockchain.getBlockHeader(1).get()));
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void disconnectOnFailureToSendStatusMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData =
          BlockHeadersMessage.create(Collections.singletonList(blockchain.getBlockHeader(1).get()));
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(silManager, (cap, msg, conn) -> {});
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void disconnectOnWrongChainId() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData =
          BlockHeadersMessage.create(Collections.singletonList(blockchain.getBlockHeader(1).get()));
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(silManager, (cap, msg, conn) -> {});

      // Send status message with wrong chain
      final StatusMessage statusMessage =
          StatusMessage.builder()
              .protocolVersion(SilProtocol.LATEST.getVersion())
              .networkId(BigInteger.valueOf(2222))
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(
                  blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
              .forkId(forkId)
              .blockRange(
                  new StatusMessage.BlockRange(
                      blockchain.getEarliestBlockNumber().get(),
                      blockchain.getChainHeadBlockNumber()))
              .build();

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, statusMessage));

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void disconnectNewPoWPeers() {
    final MergePeerFilter mergePeerFilter = new MergePeerFilter();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .setMergePeerFilter(Optional.of(mergePeerFilter))
            .build()) {

      final MockPeerConnection workPeer = setupPeer(silManager, (cap, msg, conn) -> {});
      final MockPeerConnection stakePeer = setupPeer(silManager, (cap, msg, conn) -> {});

      final StatusMessage workPeerStatus =
          StatusMessage.builder()
              .protocolVersion(SilProtocol.SIL68.getVersion())
              .networkId(BigInteger.ONE)
              .totalDifficulty(blockchain.getChainHead().getTotalDifficulty().add(20))
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(
                  blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
              .forkId(forkId)
              .build();

      final StatusMessage stakePeerStatus =
          StatusMessage.builder()
              .protocolVersion(SilProtocol.LATEST.getVersion())
              .networkId(BigInteger.ONE)
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(
                  blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
              .forkId(forkId)
              .blockRange(
                  new StatusMessage.BlockRange(
                      blockchain.getEarliestBlockNumber().get(),
                      blockchain.getChainHeadBlockNumber()))
              .build();

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(stakePeer, stakePeerStatus));

      mergePeerFilter.mergeStateChanged(
          true, Optional.empty(), Optional.of(blockchain.getChainHead().getTotalDifficulty()));
      mergePeerFilter.onNewUnverifiedForkchoice(
          new ForkchoiceEvent(Hash.EMPTY, Hash.EMPTY, Hash.hash(Bytes.of(1))));
      mergePeerFilter.onNewUnverifiedForkchoice(
          new ForkchoiceEvent(Hash.EMPTY, Hash.EMPTY, Hash.hash(Bytes.of(2))));

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(workPeer, workPeerStatus));
      assertThat(workPeer.isDisconnected()).isTrue();
      assertThat(workPeer.getDisconnectReason()).isPresent();
      assertThat(workPeer.getDisconnectReason())
          .hasValue(DisconnectReason.SUBPROTOCOL_TRIGGERED_POW_DIFFICULTY);
      assertThat(stakePeer.isDisconnected()).isFalse();
    }
  }

  @Test
  public void disconnectOnMissingBlockRangeWhenSil69() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(
                ImmutableSilProtocolConfiguration.builder()
                    .maxSilCapability(SilProtocolVersion.V69)
                    .build())
            .build()) {

      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(silManager, (cap, msg, conn) -> {}, SilProtocol.SIL69);
      StatusMessage statusMessage =
          StatusMessage.builder()
              .protocolVersion(SilProtocolVersion.V68)
              .totalDifficulty(blockchain.getChainHead().getTotalDifficulty())
              .networkId(BigInteger.ONE)
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(
                  blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
              .forkId(forkId)
              .build();

      silManager.processMessage(SilProtocol.SIL69, new DefaultMessage(peer, statusMessage));
      assertThat(peer.getDisconnectReason()).isPresent();
      assertThat(peer.getDisconnectReason())
          .hasValue(DisconnectReason.SUBPROTOCOL_TRIGGERED_INVALID_STATUS_MESSAGE);
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void doNotDisconnectOnLargeMessageWithinLimits() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData = mock(MessageData.class);
      when(messageData.getSize()).thenReturn(SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);
      when(messageData.getCode()).thenReturn(SilProtocolMessages.TRANSACTIONS);
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isFalse();
    }
  }

  @Test
  public void disconnectOnWrongGenesisHash() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData =
          BlockHeadersMessage.create(Collections.singletonList(blockchain.getBlockHeader(1).get()));
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(silManager, (cap, msg, conn) -> {});

      // Send status message with wrong chain
      final StatusMessage statusMessage =
          StatusMessage.builder()
              .protocolVersion(SilProtocol.LATEST.getVersion())
              .networkId(BigInteger.ONE)
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(gen.hash())
              .forkId(forkId)
              .blockRange(
                  new StatusMessage.BlockRange(
                      blockchain.getEarliestBlockNumber().get(),
                      blockchain.getChainHeadBlockNumber()))
              .build();

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, statusMessage));

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void doNotDisconnectOnValidMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData messageData =
          GetBlockBodiesMessage.create(Collections.singletonList(gen.hash()))
              .wrapMessageData(BigInteger.ONE);
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      final ConditionFactory waitDisconnect =
          Awaitility.await().catchUncaughtExceptions().atMost(200, TimeUnit.MILLISECONDS);
      assertThatThrownBy(() -> waitDisconnect.until(peer::isDisconnected))
          .isInstanceOf(ConditionTimeoutException.class);
    }
  }

  @Test
  public void disconnectOnMalformedGetBlockAccessListsMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData malformedMessageData =
          new RawMessage(SilProtocolMessages.GET_BLOCK_ACCESS_LISTS, Bytes.fromHexString("0xc1ff"));
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, malformedMessageData));

      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void disconnectOnMalformedGetBlockBodiesMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // 0xc1ff is a valid RLP list containing one invalid-length element (not a 32-byte hash)
      final MessageData malformedMessageData =
          new RawMessage(SilProtocolMessages.GET_BLOCK_BODIES, Bytes.fromHexString("0xc1ff"));
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, malformedMessageData));

      assertThat(peer.isDisconnected()).isTrue();
      assertThat(peer.getDisconnectReason())
          .contains(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    }
  }

  @Test
  public void disconnectOnMalformedGetPooledTransactionsMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final MessageData malformedMessageData =
          new RawMessage(
              SilProtocolMessages.GET_POOLED_TRANSACTIONS, Bytes.fromHexString("0xc1ff"));
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, malformedMessageData));

      assertThat(peer.isDisconnected()).isTrue();
      assertThat(peer.getDisconnectReason())
          .contains(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    }
  }

  @Test
  public void disconnectOnMalformedGetReceiptsMessage() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // 0xc1ff is a valid RLP list containing one invalid-length element (not a 32-byte hash)
      final MessageData malformedMessageData =
          new RawMessage(SilProtocolMessages.GET_RECEIPTS, Bytes.fromHexString("0xc1ff"));
      final MockPeerConnection peer = setupPeer(silManager, (cap, msg, conn) -> {});

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, malformedMessageData));

      assertThat(peer.isDisconnected()).isTrue();
      assertThat(peer.getDisconnectReason())
          .contains(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    }
  }

  @Test
  public void respondToGetHeaders() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      final long startBlock = 5L;
      final int blockCount = 5;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 0, false)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(startBlock + i);
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetHeadersWithinLimits() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    final int limit = 5;
    final SilProtocolConfiguration config =
        ImmutableSilProtocolConfiguration.builder().maxGetBlockHeaders(limit).build();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(config)
            .build()) {
      final long startBlock = 5L;
      final int blockCount = 10;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 0, false)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(limit);
            for (int i = 0; i < limit; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(startBlock + i);
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetHeadersReversed() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long endBlock = 10L;
      final int blockCount = 5;
      final MessageData messageData =
          GetBlockHeadersMessage.create(endBlock, blockCount, 0, true)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(endBlock - i);
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetHeadersWithSkip() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long startBlock = 5L;
      final int blockCount = 5;
      final int skip = 1;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 1, false)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(startBlock + i * (skip + 1));
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetHeadersReversedWithSkip()
      throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long endBlock = 10L;
      final int blockCount = 5;
      final int skip = 1;
      final MessageData messageData =
          GetBlockHeadersMessage.create(endBlock, blockCount, skip, true)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(endBlock - i * (skip + 1));
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  private MockPeerConnection setupPeer(
      final SilProtocolManager silManager, final PeerSendHandler onSend) {
    return setupPeer(silManager, onSend, SilProtocol.LATEST);
  }

  private MockPeerConnection setupPeer(
      final SilProtocolManager silManager,
      final PeerSendHandler onSend,
      final Capability capability) {
    final MockPeerConnection peerConnection =
        setupPeerWithoutStatusExchange(silManager, onSend, capability);
    final StatusMessage statusMessage =
        StatusMessage.builder()
            .protocolVersion(capability.getVersion())
            .networkId(BigInteger.ONE)
            .bestHash(blockchain.getChainHeadHash())
            .genesisHash(
                blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
            .forkId(forkId)
            .apply(
                builder -> {
                  if (SilProtocol.isSil69Compatible(capability)) {
                    builder.blockRange(
                        new StatusMessage.BlockRange(10L, blockchain.getChainHeadBlockNumber()));
                  } else {
                    builder.totalDifficulty(blockchain.getChainHead().getTotalDifficulty());
                  }
                })
            .build();
    silManager.processMessage(capability, new DefaultMessage(peerConnection, statusMessage));
    final SilPeers silPeers = silManager.silContext().getSilPeers();
    final SilPeer silPeer = silPeers.peer(peerConnection);
    silPeers.addPeerToSilPeers(silPeer);
    return peerConnection;
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final SilProtocolManager silManager, final PeerSendHandler onSend) {
    return setupPeerWithoutStatusExchange(silManager, onSend, SilProtocol.LATEST);
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final SilProtocolManager silManager,
      final PeerSendHandler onSend,
      final Capability capability) {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(capability));
    final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
    silManager.handleNewConnection(peer);
    return peer;
  }

  @Test
  public void respondToGetHeadersPartial() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long startBlock = blockchain.getChainHeadBlockNumber() - 1L;
      final int blockCount = 5;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 0, false)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(2);
            for (int i = 0; i < 2; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(startBlock + i);
            }
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetHeadersEmpty() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long startBlock = blockchain.getChainHeadBlockNumber() + 1;
      final int blockCount = 5;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 0, false)
              .wrapMessageData(BigInteger.ONE);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).isEmpty();
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetBodies() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      // Setup blocks query
      final long startBlock = blockchain.getChainHeadBlockNumber() - 5;
      final int blockCount = 2;
      final Block[] expectedBlocks = new Block[blockCount];
      for (int i = 0; i < blockCount; i++) {
        final BlockHeader header = blockchain.getBlockHeader(startBlock + i).get();
        final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
        expectedBlocks[i] = new Block(header, body);
      }
      final List<Hash> hashes =
          Arrays.stream(expectedBlocks).map(Block::getHash).collect(Collectors.toList());
      final MessageData messageData =
          GetBlockBodiesMessage.create(hashes).wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_BODIES);
            final BlockBodiesMessage blocksMessage =
                BlockBodiesMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockBody> bodies =
                Lists.newArrayList(blocksMessage.bodies(protocolSchedule));
            assertThat(bodies).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(expectedBlocks[i].getBody()).isEqualTo(bodies.get(i));
            }
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetBodiesWithinLimits() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    final int limit = 5;
    final SilProtocolConfiguration config =
        ImmutableSilProtocolConfiguration.builder().maxGetBlockBodies(limit).build();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(config)
            .build()) {
      // Setup blocks query
      final int blockCount = 10;
      final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
      final Block[] expectedBlocks = new Block[blockCount];
      for (int i = 0; i < blockCount; i++) {
        final BlockHeader header = blockchain.getBlockHeader(startBlock + i).get();
        final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
        expectedBlocks[i] = new Block(header, body);
      }
      final List<Hash> hashes =
          Arrays.stream(expectedBlocks).map(Block::getHash).collect(Collectors.toList());
      final MessageData messageData =
          GetBlockBodiesMessage.create(hashes).wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_BODIES);
            final BlockBodiesMessage blocksMessage =
                BlockBodiesMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockBody> bodies =
                Lists.newArrayList(blocksMessage.bodies(protocolSchedule));
            assertThat(bodies).hasSize(limit);
            for (int i = 0; i < limit; i++) {
              assertThat(expectedBlocks[i].getBody()).isEqualTo(bodies.get(i));
            }
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetBodiesPartial() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // Setup blocks query
      final long expectedBlockNumber = blockchain.getChainHeadBlockNumber() - 1;
      final BlockHeader header = blockchain.getBlockHeader(expectedBlockNumber).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      final Block expectedBlock = new Block(header, body);

      final List<Hash> hashes = Arrays.asList(gen.hash(), expectedBlock.getHash(), gen.hash());
      final MessageData messageData =
          GetBlockBodiesMessage.create(hashes).wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_BODIES);
            final BlockBodiesMessage blocksMessage =
                BlockBodiesMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockBody> bodies =
                Lists.newArrayList(blocksMessage.bodies(protocolSchedule));
            assertThat(bodies).hasSize(1);
            assertThat(expectedBlock.getBody()).isEqualTo(bodies.get(0));
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetReceipts() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // Setup blocks query
      final long startBlock = blockchain.getChainHeadBlockNumber() - 5;
      final int blockCount = 2;
      final List<List<TransactionReceipt>> expectedReceipts = new ArrayList<>(blockCount);
      final List<Hash> blockHashes = new ArrayList<>(blockCount);
      for (int i = 0; i < blockCount; i++) {
        final BlockHeader header = blockchain.getBlockHeader(startBlock + i).get();
        expectedReceipts.add(blockchain.getTxReceipts(header.getHash()).get());
        blockHashes.add(header.getHash());
      }
      final MessageData messageData =
          GetReceiptsMessage.create(blockHashes).wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.RECEIPTS);
            final ReceiptsMessage receiptsMessage =
                ReceiptsMessage.readFrom(message.unwrapMessageData().getValue());
            final List<List<TransactionReceipt>> receipts =
                receiptsMessage.syncReceipts().stream().map(Utils::syncReceiptsToReceipts).toList();
            assertThat(receipts).hasSize(blockCount);
            for (int i = 0; i < blockCount; i++) {
              assertThat(expectedReceipts.get(i)).isEqualTo(receipts.get(i));
            }
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.SIL69, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetReceiptsWithinLimits() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    final int limit = 5;
    final SilProtocolConfiguration config =
        ImmutableSilProtocolConfiguration.builder().maxGetReceipts(limit).build();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(config)
            .build()) {
      // Setup blocks query
      final int blockCount = 10;
      final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
      final List<List<TransactionReceipt>> expectedReceipts = new ArrayList<>(blockCount);
      final List<Hash> blockHashes = new ArrayList<>(blockCount);
      for (int i = 0; i < blockCount; i++) {
        final BlockHeader header = blockchain.getBlockHeader(startBlock + i).get();
        expectedReceipts.add(blockchain.getTxReceipts(header.getHash()).get());
        blockHashes.add(header.getHash());
      }
      final MessageData messageData =
          GetReceiptsMessage.create(blockHashes).wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.RECEIPTS);
            final ReceiptsMessage receiptsMessage =
                ReceiptsMessage.readFrom(message.unwrapMessageData().getValue());
            final List<List<TransactionReceipt>> receipts =
                receiptsMessage.syncReceipts().stream().map(Utils::syncReceiptsToReceipts).toList();
            assertThat(receipts).hasSize(limit);
            for (int i = 0; i < limit; i++) {
              assertThat(expectedReceipts.get(i)).isEqualTo(receipts.get(i));
            }
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.SIL69, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void respondToGetReceiptsPartial() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // Setup blocks query
      final long blockNumber = blockchain.getChainHeadBlockNumber() - 5;
      final BlockHeader header = blockchain.getBlockHeader(blockNumber).get();
      final List<TransactionReceipt> expectedReceipts =
          blockchain.getTxReceipts(header.getHash()).get();
      final Hash blockHash = header.getHash();
      final MessageData messageData =
          GetReceiptsMessage.create(Arrays.asList(gen.hash(), blockHash, gen.hash()))
              .wrapMessageData(BigInteger.ONE);

      // Define handler to validate response
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.RECEIPTS);
            final ReceiptsMessage receiptsMessage =
                ReceiptsMessage.readFrom(message.unwrapMessageData().getValue());
            final List<List<TransactionReceipt>> receipts =
                receiptsMessage.syncReceipts().stream().map(Utils::syncReceiptsToReceipts).toList();
            assertThat(receipts).hasSize(1);
            assertThat(expectedReceipts).isEqualTo(receipts.get(0));
            done.complete(null);
          };

      // Run test
      final PeerConnection peer = setupPeer(silManager, onSend);
      silManager.processMessage(SilProtocol.SIL69, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void newBlockMinedSendsNewBlockMessageToAllPeers() {
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {
      // Define handler to validate response
      final PeerSendHandler onSend = mock(PeerSendHandler.class);
      final List<PeerConnection> peers = Lists.newArrayList();

      final int PEER_COUNT = 5;
      for (int i = 0; i < PEER_COUNT; i++) {
        peers.add(setupPeer(silManager, onSend));
      }

      final Hash chainHeadHash = blockchain.getChainHeadHash();
      final Block minedBlock =
          new Block(
              blockchain.getBlockHeader(chainHeadHash).get(),
              blockchain.getBlockBody(chainHeadHash).get());

      final Difficulty expectedTotalDifficulty = blockchain.getChainHead().getTotalDifficulty();

      reset(onSend);

      silManager.blockMined(minedBlock);

      final ArgumentCaptor<NewBlockMessage> messageSentCaptor =
          ArgumentCaptor.forClass(NewBlockMessage.class);
      final ArgumentCaptor<PeerConnection> receivingPeerCaptor =
          ArgumentCaptor.forClass(PeerConnection.class);
      final ArgumentCaptor<Capability> capabilityCaptor = ArgumentCaptor.forClass(Capability.class);

      verify(onSend, times(PEER_COUNT))
          .exec(
              capabilityCaptor.capture(),
              messageSentCaptor.capture(),
              receivingPeerCaptor.capture());

      // assert that all entries in capability param were latest
      assertThat(capabilityCaptor.getAllValues().stream().distinct().collect(Collectors.toList()))
          .isEqualTo(Collections.singletonList(SilProtocol.LATEST));

      // assert that all messages transmitted contain the expected block & total difficulty.
      final ProtocolSchedule protocolSchdeule = ProtocolScheduleFixture.TESTING_NETWORK;
      for (final NewBlockMessage msg : messageSentCaptor.getAllValues()) {
        assertThat(msg.block(protocolSchdeule)).isEqualTo(minedBlock);
        assertThat(msg.totalDifficulty(protocolSchdeule)).isEqualTo(expectedTotalDifficulty);
      }

      assertThat(receivingPeerCaptor.getAllValues()).containsAll(peers);
    }
  }

  @Test
  public void shouldSuccessfullyRespondToGetHeadersRequestLessThanZero()
      throws ExecutionException, InterruptedException {
    final Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain = createInMemoryBlockchain(genesisBlock);

    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(1L)
            .setParentHash(blockchain.getBlockHashByNumber(0L).get());
    final Block block = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(block);
    blockchain.appendBlock(block, receipts);

    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      final long startBlock = 1L;
      final int requestedBlockCount = 13;
      final int receivedBlockCount = 2;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, requestedBlockCount, 0, true)
              .wrapMessageData(BigInteger.ONE);
      final MockPeerConnection.PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == SilProtocolMessages.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(SilProtocolMessages.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg =
                BlockHeadersMessage.readFrom(message.unwrapMessageData().getValue());
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers).hasSize(receivedBlockCount);
            for (int i = 0; i < receivedBlockCount; i++) {
              assertThat(headers.get(i).getNumber()).isEqualTo(receivedBlockCount - 1 - i);
            }
            done.complete(null);
          };

      final Set<Capability> caps = new HashSet<>(Collections.singletonList(SilProtocol.LATEST));
      final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
      silManager.handleNewConnection(peer);
      final StatusMessage statusMessage =
          StatusMessage.builder()
              .protocolVersion(SilProtocol.LATEST.getVersion())
              .networkId(BigInteger.ONE)
              .bestHash(blockchain.getChainHeadHash())
              .genesisHash(
                  blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash())
              .forkId(forkId)
              .blockRange(
                  new StatusMessage.BlockRange(
                      blockchain.getEarliestBlockNumber().get(),
                      blockchain.getChainHeadBlockNumber()))
              .build();

      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, statusMessage));
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, messageData));
      done.get();
    }
  }

  @Test
  public void transactionMessagesGoToTheCorrectExecutor() {
    // Create a mock silScheduler to hold our mock executors.
    final ExecutorService worker = mock(ExecutorService.class);
    final ScheduledExecutorService scheduled = mock(ScheduledExecutorService.class);
    final ExecutorService transactions = mock(ExecutorService.class);
    final ExecutorService services = mock(ExecutorService.class);
    final ExecutorService computations = mock(ExecutorService.class);
    final ExecutorService blockCreation = mock(ExecutorService.class);
    final SilScheduler silScheduler =
        new SilScheduler(worker, scheduled, transactions, services, computations, blockCreation);

    // Create the fake TransactionMessage to feed to the SilManager.
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<Transaction> txes = Collections.singletonList(gen.transaction());
    final MessageData initialMessage = TransactionsMessage.create(txes);
    final MessageData raw =
        new RawMessage(SilProtocolMessages.TRANSACTIONS, initialMessage.getData());
    final TransactionsMessage transactionMessage = TransactionsMessage.readFrom(raw);
    final SilProtocolConfiguration silProtocolConfiguration = SilProtocolConfiguration.DEFAULT;
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(silScheduler)
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(silProtocolConfiguration)
            .build()) {
      // Create a transaction pool.  This has a side effect of registering a listener for the
      // transactions message.
      TransactionPoolFactory.createTransactionPool(
              protocolSchedule,
              protocolContext,
              silManager.silContext(),
              TestClock.system(ZoneId.systemDefault()),
              metricsSystem,
              new SyncState(blockchain, silManager.silContext().getSilPeers()),
              TransactionPoolConfiguration.DEFAULT,
              silProtocolConfiguration,
              new BlobCache(),
              MiningConfiguration.newDefault())
          .setEnabled();

      // Send just a transaction message.
      final PeerConnection peer = setupPeer(silManager, (cap, msg, connection) -> {});
      silManager.processMessage(SilProtocol.LATEST, new DefaultMessage(peer, transactionMessage));

      // Verify the regular message executor execute.
      verifyNoInteractions(worker);
      // Verify that the scheduled executor scheduled two tasks
      verify(scheduled, times(2)).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
      // Verify our transactions executor got something to execute.
      verify(transactions).execute(any());
    }
  }

  @Test
  public void shouldUseRightCapabilityDependingOnSyncMode() {
    assertHighestCapability(SyncMode.SNAP, SilProtocol.LATEST);
    assertHighestCapability(SyncMode.FULL, SilProtocol.LATEST);
  }

  @Test
  public void shouldRespectFlagForMaxCapability() {

    // Test with max capability = 65. should respect flag
    final SilProtocolConfiguration configuration =
        ImmutableSilProtocolConfiguration.builder()
            .maxSilCapability(SilProtocolVersion.V68)
            .build();

    assertHighestCapability(SyncMode.SNAP, SilProtocol.SIL68, configuration);
    assertHighestCapability(SyncMode.FULL, SilProtocol.SIL68, configuration);
  }

  @Test
  public void shouldRespectFlagForMinCapability() {

    // If min cap = v67, should not contain v66
    final SilProtocolConfiguration configuration =
        ImmutableSilProtocolConfiguration.builder()
            .minSilCapability(SilProtocolVersion.V69)
            .build();

    final SilProtocolManager silManager = createSilManager(SyncMode.SNAP, configuration);

    assertThat(silManager.getSupportedCapabilities()).contains(SilProtocol.SIL69);
    assertThat(silManager.getSupportedCapabilities()).doesNotContain(SilProtocol.SIL68);
  }

  @Test
  public void shouldRespectProtocolForMaxCapabilityIfFlagGreaterThanProtocol() {

    // Test with max capability = 68. should respect protocol
    final SilProtocolConfiguration configuration =
        ImmutableSilProtocolConfiguration.builder()
            .maxSilCapability(SilProtocolVersion.V68)
            .build();

    assertHighestCapability(SyncMode.SNAP, SilProtocol.SIL68, configuration);
    assertHighestCapability(SyncMode.FULL, SilProtocol.SIL68, configuration);
  }

  @Test
  public void shouldThrowExceptionWhenNoCapabilities() {
    final SilProtocolConfiguration configuration =
        ImmutableSilProtocolConfiguration.builder()
            .minSilCapability(SilProtocolVersion.V69)
            .maxSilCapability(SilProtocolVersion.V68)
            .build();

    assertThatThrownBy(() -> createSilManager(SyncMode.SNAP, configuration))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No supported Sil protocol capabilities found. Check the configuration for min and max Sil protocol versions.");
  }

  private void assertHighestCapability(final SyncMode syncMode, final Capability capability) {
    assertHighestCapability(syncMode, capability, SilProtocolConfiguration.DEFAULT);
  }

  private void assertHighestCapability(
      final SyncMode syncMode,
      final Capability capability,
      final SilProtocolConfiguration silProtocolConfiguration) {

    final SilProtocolManager silManager = createSilManager(syncMode, silProtocolConfiguration);

    assertThat(capability.getVersion()).isEqualTo(silManager.getHighestProtocolVersion());
  }

  private SilProtocolManager createSilManager(
      final SyncMode syncMode, final SilProtocolConfiguration silProtocolConfiguration) {
    final SynchronizerConfiguration syncConfig = mock(SynchronizerConfiguration.class);
    when(syncConfig.getSyncMode()).thenReturn(syncMode);
    SilContext silContext = mock(SilContext.class);
    when(silContext.getSilMessages()).thenReturn(mock(SilMessages.class));
    when(silContext.getScheduler()).thenReturn(mock(SilScheduler.class));
    try (final SilProtocolManager silManager =
        new SilProtocolManager(
            blockchain,
            BigInteger.ONE,
            mock(WorldStateArchive.class),
            transactionPool,
            silProtocolConfiguration,
            mock(SilPeers.class),
            mock(SilMessages.class),
            silContext,
            Collections.emptyList(),
            Optional.empty(),
            syncConfig,
            mock(SilScheduler.class),
            mock(ForkIdManager.class))) {

      return silManager;
    }
  }

  @Test
  public void shouldSendEarliestBlockToPeerWhenCapabilitySil69() {
    long expectedEarliestBlock = 10L;
    Blockchain blockChainMock = spy(blockchain);
    when(blockChainMock.getEarliestBlockNumber()).thenReturn(Optional.of(expectedEarliestBlock));
    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockChainMock)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      setupPeerWithoutStatusExchange(
          silManager,
          (cap, msg, conn) -> {
            assertThat(msg.getCode() == SilProtocolMessages.STATUS).isTrue();
            long earliestBlock =
                StatusMessage.create(msg.getData()).blockRange().orElseThrow().earliestBlock();
            assertThat(earliestBlock).isEqualTo(expectedEarliestBlock);
          },
          SilProtocol.SIL69);
    }
  }

  @Test
  public void shouldSendCorrectEarliestBlockToPeerWhenEarliestIsNotSet() {
    Blockchain blockChainMock = spy(blockchain);
    when(blockChainMock.getEarliestBlockNumber()).thenReturn(Optional.empty());

    try (final SilProtocolManager silManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockChainMock)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build()) {

      setupPeerWithoutStatusExchange(
          silManager,
          (cap, msg, conn) -> {
            assertThat(msg.getCode() == SilProtocolMessages.STATUS).isTrue();
            long earliestBlock =
                StatusMessage.create(msg.getData()).blockRange().orElseThrow().earliestBlock();
            assertThat(earliestBlock).isEqualTo(0L);
          },
          SilProtocol.SIL69);
    }
  }
}
