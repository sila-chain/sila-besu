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
package org.hyperledger.besu.sila.sil.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.ConsensusContext;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.BadBlockCause;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.BlockImporter;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer.Responder;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTaskExecutorAnswer;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.messages.NewBlockHashesMessage;
import org.hyperledger.besu.sila.sil.messages.NewBlockMessage;
import org.hyperledger.besu.sila.sil.sync.BlockPropagationManager.ProcessingBlocksManager;
import org.hyperledger.besu.sila.sil.sync.state.PendingBlocksManager;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetBlockHeaderFunctions;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpec;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.number.ByteUnits;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public abstract class AbstractBlockPropagationManagerTest {

  private static final Bytes NODE_ID_1 = Bytes.fromHexString("0x00");

  protected BlockchainSetupUtil blockchainUtil;
  protected ProtocolSchedule protocolSchedule;
  protected ProtocolContext protocolContext;
  protected MutableBlockchain blockchain;
  protected BlockBroadcaster blockBroadcaster;
  protected SilProtocolManager silProtocolManager;
  protected BlockPropagationManager blockPropagationManager;
  protected SynchronizerConfiguration syncConfig;
  private PeerTaskExecutor peerTaskExecutor;
  protected final PendingBlocksManager pendingBlocksManager =
      spy(
          new PendingBlocksManager(
              SynchronizerConfiguration.builder().blockPropagationRange(-10, 30).build()));
  protected final ProcessingBlocksManager processingBlocksManager =
      spy(new ProcessingBlocksManager());
  protected SyncState syncState;
  protected final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final Hash finalizedHash = Hash.fromHexStringLenient("0x1337");
  private final int maxMessageSize = 10 * ByteUnits.MEGABYTE;

  protected void setup(final DataStorageFormat dataStorageFormat) {
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    blockchainUtil = BlockchainSetupUtil.forTesting(dataStorageFormat);
    blockchain = blockchainUtil.getBlockchain();
    protocolSchedule = blockchainUtil.getProtocolSchedule();
    final ProtocolContext tempProtocolContext = blockchainUtil.getProtocolContext();
    protocolContext =
        new ProtocolContext.Builder()
            .withBlockchain(blockchain)
            .withWorldStateArchive(tempProtocolContext.getWorldStateArchive())
            .withConsensusContext(tempProtocolContext.getConsensusContext(ConsensusContext.class))
            .build();
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setWorldStateArchive(blockchainUtil.getWorldArchive())
            .setTransactionPool(blockchainUtil.getTransactionPool())
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    syncConfig = SynchronizerConfiguration.builder().blockPropagationRange(-3, 5).build();
    syncState = new SyncState(blockchain, silProtocolManager.silContext().getSilPeers());
    blockBroadcaster = mock(BlockBroadcaster.class);
    blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silProtocolManager.silContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster,
            processingBlocksManager);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(
            new GetHeadersFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(
            new GetHeadersFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetBodiesFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(
            new GetBodiesFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetBodiesFromPeerTask.class)))
        .thenAnswer(
            new GetBodiesFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));
  }

  @Test
  public void importsAnnouncedBlocks_aheadOfChainInOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup additional peer for best peers list
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockHashesMessage nextNextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextNextBlock.getHash(), nextNextBlock.getHeader().getNumber())));
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast second message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedBlocks_aheadOfChainOutOfOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockHashesMessage nextNextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextNextBlock.getHash(), nextNextBlock.getHeader().getNumber())));
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast second message first
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedNewBlocks_aheadOfChainInOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(
            nextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get(),
            maxMessageSize);
    final NewBlockMessage nextNextAnnouncement =
        NewBlockMessage.create(
            nextNextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextNextBlock.getHash()).get(),
            maxMessageSize);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast second message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsAnnouncedNewBlocks_aheadOfChainOutOfOrder() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);
    final Block nextNextBlock = blockchainUtil.getBlock(3);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(
            nextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get(),
            maxMessageSize);
    final NewBlockMessage nextNextAnnouncement =
        NewBlockMessage.create(
            nextNextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextNextBlock.getHash()).get(),
            maxMessageSize);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast second message first
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextNextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    assertThat(blockchain.contains(nextNextBlock.getHash())).isTrue();
  }

  @Test
  public void importsMixedOutOfOrderMessages() {
    blockchainUtil.importFirstBlocks(2);
    final Block block1 = blockchainUtil.getBlock(2);
    final Block block2 = blockchainUtil.getBlock(3);
    final Block block3 = blockchainUtil.getBlock(4);
    final Block block4 = blockchainUtil.getBlock(5);

    // Sanity check
    assertThat(blockchain.contains(block1.getHash())).isFalse();
    assertThat(blockchain.contains(block2.getHash())).isFalse();
    assertThat(blockchain.contains(block3.getHash())).isFalse();
    assertThat(blockchain.contains(block4.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage block1Msg =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    block1.getHash(), block1.getHeader().getNumber())));
    final NewBlockMessage block2Msg =
        NewBlockMessage.create(
            block2,
            getFullBlockchain().getTotalDifficultyByHash(block2.getHash()).get(),
            maxMessageSize);
    final NewBlockHashesMessage block3Msg =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    block3.getHash(), block3.getHeader().getNumber())));
    final NewBlockMessage block4Msg =
        NewBlockMessage.create(
            block4,
            getFullBlockchain().getTotalDifficultyByHash(block4.getHash()).get(),
            maxMessageSize);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast older blocks
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, block3Msg);
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, block4Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, block2Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast first block
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, block1Msg);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(block1.getHash())).isTrue();
    assertThat(blockchain.contains(block2.getHash())).isTrue();
    assertThat(blockchain.contains(block3.getHash())).isTrue();
    assertThat(blockchain.contains(block4.getHash())).isTrue();
  }

  @Test
  public void handlesDuplicateAnnouncements() {

    final ProtocolSchedule stubProtocolSchedule = spy(protocolSchedule);
    final ProtocolSpec stubProtocolSpec = spy(protocolSchedule.getByBlockHeader(blockHeader(2)));
    final BlockImporter stubBlockImporter = spy(stubProtocolSpec.getBlockImporter());
    doReturn(stubProtocolSpec).when(stubProtocolSchedule).getByBlockHeader(any());
    doReturn(stubBlockImporter).when(stubProtocolSpec).getBlockImporter();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            stubProtocolSchedule,
            protocolContext,
            silProtocolManager.silContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage newBlockHash =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockMessage newBlock =
        NewBlockMessage.create(
            nextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get(),
            maxMessageSize);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlock);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast duplicate
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlockHash);
    peer.respondWhile(responder, peer::hasOutstandingRequests);
    // Broadcast duplicate
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlock);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    verify(stubBlockImporter, times(1)).importBlock(eq(protocolContext), eq(nextBlock), any());
  }

  @Test
  public void handlesPendingDuplicateAnnouncements() {
    final ProtocolSchedule stubProtocolSchedule = spy(protocolSchedule);
    final ProtocolSpec stubProtocolSpec = spy(protocolSchedule.getByBlockHeader(blockHeader(2)));
    final BlockImporter stubBlockImporter = spy(stubProtocolSpec.getBlockImporter());
    doReturn(stubProtocolSpec).when(stubProtocolSchedule).getByBlockHeader(any());
    doReturn(stubBlockImporter).when(stubProtocolSpec).getBlockImporter();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            stubProtocolSchedule,
            protocolContext,
            silProtocolManager.silContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage newBlockHash =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final NewBlockMessage newBlock =
        NewBlockMessage.create(
            nextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get(),
            maxMessageSize);

    // Broadcast messages
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlock);
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlockHash);
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlock);
    // Respond
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
    verify(stubBlockImporter, times(1)).importBlock(eq(protocolContext), eq(nextBlock), any());
  }

  @Test
  public void ignoresFutureNewBlockHashAnnouncement() {
    blockchainUtil.importFirstBlocks(2);
    final Block futureBlock = blockchainUtil.getBlock(11);

    // Sanity check
    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage futureAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    futureBlock.getHash(), futureBlock.getHeader().getNumber())));

    // Broadcast
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, futureAnnouncement);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresFutureNewBlockAnnouncement() {
    blockchainUtil.importFirstBlocks(2);
    final Block futureBlock = blockchainUtil.getBlock(11);

    // Sanity check
    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage futureAnnouncement =
        NewBlockMessage.create(
            futureBlock,
            getFullBlockchain().getTotalDifficultyByHash(futureBlock.getHash()).get(),
            maxMessageSize);

    // Broadcast
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, futureAnnouncement);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockchain.contains(futureBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresOldNewBlockHashAnnouncement() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(10);
    final Block blockOne = blockchainUtil.getBlock(1);
    final Block oldBlock = gen.nextBlock(blockOne);

    // Sanity check
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();

    final BlockPropagationManager propManager = spy(blockPropagationManager);
    propManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage oldAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    oldBlock.getHash(), oldBlock.getHeader().getNumber())));

    // Broadcast
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, oldAnnouncement);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(propManager, times(0)).importOrSavePendingBlock(any(), any(Bytes.class));
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();
  }

  @Test
  public void ignoresOldNewBlockAnnouncement() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    blockchainUtil.importFirstBlocks(10);
    final Block blockOne = blockchainUtil.getBlock(1);
    final Block oldBlock = gen.nextBlock(blockOne);

    // Sanity check
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();

    final BlockPropagationManager propManager = spy(blockPropagationManager);
    propManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage oldAnnouncement =
        NewBlockMessage.create(oldBlock, Difficulty.ZERO, maxMessageSize);

    // Broadcast
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, oldAnnouncement);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(propManager, times(0)).importOrSavePendingBlock(any(), any(Bytes.class));
    assertThat(blockchain.contains(oldBlock.getHash())).isFalse();
  }

  @Test
  public void purgesOldBlocks() {
    final int oldBlocksToImport = 3;
    syncConfig =
        SynchronizerConfiguration.builder().blockPropagationRange(-oldBlocksToImport, 5).build();
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silProtocolManager.silContext(),
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    final BlockDataGenerator gen = new BlockDataGenerator();
    // Import some blocks
    blockchainUtil.importFirstBlocks(5);
    // Set up test block next to head, that should eventually be purged
    final Block blockToPurge =
        gen.block(BlockOptions.create().setBlockNumber(blockchain.getChainHeadBlockNumber()));

    blockPropagationManager.start();
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage blockAnnouncementMsg =
        NewBlockMessage.create(blockToPurge, Difficulty.ZERO, maxMessageSize);

    // Broadcast
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, blockAnnouncementMsg);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    // Check that we pushed our block into the pending collection
    assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
    assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isTrue();

    // Import blocks until we bury the target block far enough to be cleaned up
    for (int i = 0; i < oldBlocksToImport; i++) {
      blockchainUtil.importBlockAtIndex((int) blockchain.getChainHeadBlockNumber() + 1);

      assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
      assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isTrue();
    }

    // Import again to trigger cleanup
    blockchainUtil.importBlockAtIndex((int) blockchain.getChainHeadBlockNumber() + 1);
    assertThat(blockchain.contains(blockToPurge.getHash())).isFalse();
    assertThat(pendingBlocksManager.contains(blockToPurge.getHash())).isFalse();
  }

  @Test
  public void updatesChainHeadWhenNewBlockMessageReceived() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final Difficulty parentTotalDifficulty =
        getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHeader().getParentHash()).get();
    final Difficulty totalDifficulty =
        getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get();
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(nextBlock, totalDifficulty, maxMessageSize);

    // Broadcast message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(peer.getSilPeer().chainState().getBestBlock().getHash())
        .isEqualTo(nextBlock.getHeader().getParentHash());
    assertThat(peer.getSilPeer().chainState().getEstimatedHeight())
        .isEqualTo(nextBlock.getHeader().getNumber() - 1);
    assertThat(peer.getSilPeer().chainState().getBestBlock().getTotalDifficulty())
        .isEqualTo(parentTotalDifficulty);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotImportBlocksThatAreAlreadyBeingImported() {
    final SilScheduler silScheduler = mock(SilScheduler.class);
    when(silScheduler.scheduleSyncWorkerTask(any(Supplier.class)))
        .thenReturn(new CompletableFuture<>());
    final SilContext silContext =
        new SilContext(
            new SilPeers(
                () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()),
                TestClock.fixed(),
                metricsSystem,
                SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
                Collections.emptyList(),
                Bytes.random(64),
                25,
                25,
                false,
                SyncMode.SNAP,
                new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList())),
            new SilMessages(),
            silScheduler,
            null);
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silContext,
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    blockPropagationManager.importOrSavePendingBlock(nextBlock, NODE_ID_1);
    blockPropagationManager.importOrSavePendingBlock(nextBlock, NODE_ID_1);

    verify(silScheduler, times(1)).scheduleSyncWorkerTask(any(Supplier.class));
  }

  @Test
  public void shouldRequestLowestAnnouncedPendingBlockParent() {
    // test if block propagation manager can recover if one block is missed

    blockchainUtil.importFirstBlocks(2);
    final List<Block> blocks = blockchainUtil.getBlocks().subList(2, 4);

    blockPropagationManager.start();

    // Create peer and responder
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // skip first block then create messages from blocklist
    blocks.stream()
        .skip(1)
        .map(this::createNewBlockHashMessage)
        .forEach(
            message -> { // Broadcast new block hash message
              SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, message);
            });

    peer.respondWhile(responder, peer::hasOutstandingRequests);

    // assert all blocks were imported
    blocks.forEach(
        block -> {
          assertThat(blockchain.contains(block.getHash())).isTrue();
        });
  }

  @Test
  public void shouldRequestLowestAnnouncedPendingBlockParent_twoMissingBlocks() {
    // test if block propagation manager can recover if one block is missed
    blockchainUtil.importFirstBlocks(2);
    final List<Block> blocks = blockchainUtil.getBlocks().subList(2, 6);

    blockPropagationManager.start();

    // Create peer and responder
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    // skip two block then create messages from blocklist
    blocks.stream()
        .skip(2)
        .map(this::createNewBlockHashMessage)
        .forEach(
            message -> { // Broadcast new block hash message
              SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, message);
            });

    peer.respondWhile(responder, peer::hasOutstandingRequests);

    // assert all blocks were imported
    blocks.forEach(
        block -> {
          assertThat(blockchain.contains(block.getHash())).isTrue();
        });
  }

  private NewBlockHashesMessage createNewBlockHashMessage(final Block block) {
    return NewBlockHashesMessage.create(
        Collections.singletonList(
            new NewBlockHashesMessage.NewBlockHash(
                block.getHash(), block.getHeader().getNumber())));
  }

  @Test
  public void verifyBroadcastBlockInvocation() {
    blockchainUtil.importFirstBlocks(2);
    final Block block = blockchainUtil.getBlock(2);
    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);

    final Difficulty totalDifficulty =
        getFullBlockchain().getTotalDifficultyByHash(block.getHash()).get();
    final NewBlockMessage newBlockMessage =
        NewBlockMessage.create(block, totalDifficulty, maxMessageSize);

    // Broadcast message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlockMessage);

    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    verify(blockBroadcaster, times(1)).propagate(block, totalDifficulty);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldDetectAndCacheInvalidBlocks() {
    final SilScheduler silScheduler = mock(SilScheduler.class);
    when(silScheduler.scheduleSyncWorkerTask(any(Supplier.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(final InvocationOnMock invocation) throws Throwable {
                return invocation.getArgument(0, Supplier.class).get();
              }
            });

    final SilContext silContext =
        new SilContext(
            new SilPeers(
                () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()),
                TestClock.fixed(),
                metricsSystem,
                SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
                Collections.emptyList(),
                Bytes.random(64),
                25,
                25,
                false,
                SyncMode.SNAP,
                new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList())),
            new SilMessages(),
            silScheduler,
            null);
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silContext,
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block firstBlock = blockchainUtil.getBlock(1);
    final BadBlockManager badBlocksManager = protocolContext.getBadBlockManager();
    final Block badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(1)
                    .setParentHash(firstBlock.getHash())
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    assertThat(badBlocksManager.getBadBlocks()).isEmpty();
    blockPropagationManager.importOrSavePendingBlock(badBlock, NODE_ID_1);
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);

    verify(silScheduler, times(1)).scheduleSyncWorkerTask(any(Supplier.class));
  }

  @Test
  public void shouldSkipKnownBadBlockOnNewBlockMessage() {
    blockchainUtil.importFirstBlocks(2);
    final Block firstBlock = blockchainUtil.getBlock(1);
    final BadBlockManager badBlocksManager = protocolContext.getBadBlockManager();
    final Block badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(2)
                    .setParentHash(firstBlock.getHash())
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    // Pre-populate BadBlockManager, simulating an earlier validation failure.
    badBlocksManager.addBadBlock(badBlock, BadBlockCause.fromValidationFailure("test"));
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);

    final BlockPropagationManager propManager = spy(blockPropagationManager);
    propManager.start();

    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage newBlockMessage =
        NewBlockMessage.create(badBlock, Difficulty.ONE, maxMessageSize);

    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlockMessage);

    // handleNewBlockFromNetwork must short-circuit before dispatching to importOrSavePendingBlock.
    // Checking addImportingBlock alone would also pass via the defensive second check inside
    // importOrSavePendingBlock, so verify the dispatch itself never happened.
    verify(propManager, never()).importOrSavePendingBlock(any(), any(Bytes.class));
    // BadBlockManager should still contain exactly one entry — we didn't re-add it.
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldSkipKnownBadBlockOnNewBlockHashesMessage() {
    blockchainUtil.importFirstBlocks(2);
    final Block firstBlock = blockchainUtil.getBlock(1);
    final BadBlockManager badBlocksManager = protocolContext.getBadBlockManager();
    final Block badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(2)
                    .setParentHash(firstBlock.getHash())
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    // Pre-populate BadBlockManager, simulating an earlier validation failure.
    badBlocksManager.addBadBlock(badBlock, BadBlockCause.fromValidationFailure("test"));

    blockPropagationManager.start();

    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage newBlockHashesMessage =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    badBlock.getHash(), badBlock.getHeader().getNumber())));

    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, newBlockHashesMessage);

    // The hash announcement should have been filtered out before requesting the body.
    verify(processingBlocksManager, never()).addRequestedBlock(badBlock.getHash());
    // No body request should have been issued to the peer.
    assertThat(peer.hasOutstandingRequests()).isFalse();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void importOrSavePendingBlockShouldSkipKnownBadBlock() {
    // Mirrors shouldDetectAndCacheInvalidBlocks but pre-populates BadBlockManager to
    // exercise the defensive short-circuit at the top of importOrSavePendingBlock.
    final SilScheduler silScheduler = mock(SilScheduler.class);
    when(silScheduler.scheduleSyncWorkerTask(any(Supplier.class)))
        .thenAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(final InvocationOnMock invocation) throws Throwable {
                return invocation.getArgument(0, Supplier.class).get();
              }
            });

    final SilContext silContext =
        new SilContext(
            new SilPeers(
                () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()),
                TestClock.fixed(),
                metricsSystem,
                SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
                Collections.emptyList(),
                Bytes.random(64),
                25,
                25,
                false,
                SyncMode.SNAP,
                new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList())),
            new SilMessages(),
            silScheduler,
            null);
    final BlockPropagationManager blockPropagationManager =
        new BlockPropagationManager(
            syncConfig,
            protocolSchedule,
            protocolContext,
            silContext,
            syncState,
            pendingBlocksManager,
            metricsSystem,
            blockBroadcaster);

    blockchainUtil.importFirstBlocks(2);
    final Block firstBlock = blockchainUtil.getBlock(1);
    final BadBlockManager badBlocksManager = protocolContext.getBadBlockManager();
    final Block badBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(2)
                    .setParentHash(firstBlock.getHash())
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    // Pre-populate BadBlockManager, simulating an earlier validation failure.
    badBlocksManager.addBadBlock(badBlock, BadBlockCause.fromValidationFailure("test"));
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);

    blockPropagationManager.importOrSavePendingBlock(badBlock, NODE_ID_1);

    // The defensive check should have short-circuited before scheduling validation.
    verify(silScheduler, never()).scheduleSyncWorkerTask(any(Supplier.class));
    assertThat(badBlocksManager.getBadBlocks().size()).isEqualTo(1);
  }

  @Test
  public void shouldTryWithAnotherPeerWhenFailedDownloadingBlock() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final RespondingSilPeer secondPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 2);

    // Pretend the second peer is busier, so the first is selected a first
    when(spy(secondPeer.getSilPeer()).outstandingRequests()).thenReturn(1);

    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));

    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(RespondingSilPeer.emptyResponder(), peer::hasOutstandingRequests);
    secondPeer.respondWhile(
        RespondingSilPeer.blockchainResponder(getFullBlockchain()),
        secondPeer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
  }

  @Test
  public void shouldThrowErrorWhenNoValidPeerAvailable() {
    Mockito.reset(peerTaskExecutor);
    Mockito.when(peerTaskExecutor.executeAgainstPeer(Mockito.any(), Mockito.any()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE,
                Collections.emptyList()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE,
                Collections.emptyList()));
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    // Setup peer and messages
    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final RespondingSilPeer secondPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);

    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));

    // Broadcast first message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(RespondingSilPeer.emptyResponder(), peer::hasOutstandingRequests);
    secondPeer.respondWhile(RespondingSilPeer.emptyResponder(), secondPeer::hasOutstandingRequests);

    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }

  @Test
  public void shouldStopWhenFinalized() {
    blockPropagationManager.start();
    // syncState.setReachedTerminalDifficulty(true);
    blockPropagationManager.onNewUnverifiedForkchoice(
        new ForkchoiceEvent(null, null, this.finalizedHash));
    assertThat(blockPropagationManager.isRunning()).isFalse();
    assertThat(silProtocolManager.silContext().getSilMessages().messageCodesHandled())
        .doesNotContain(SilProtocolMessages.NEW_BLOCK_HASHES, SilProtocolMessages.NEW_BLOCK);
  }

  @Test
  public void shouldRestartWhenTTDReachedReturnsFalseAfterFinalizing() {
    blockPropagationManager.start();
    syncState.setReachedTerminalDifficulty(true);
    blockPropagationManager.onNewUnverifiedForkchoice(
        new ForkchoiceEvent(null, null, this.finalizedHash));
    assertThat(blockPropagationManager.isRunning()).isFalse();
    syncState.setReachedTerminalDifficulty(false);
    assertThat(blockPropagationManager.isRunning()).isTrue();
  }

  @Test
  public void shouldNotListenToNewBlockHashesAnnouncementsWhenTTDReachedAndFinal() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    syncState.setReachedTerminalDifficulty(true);
    blockPropagationManager.onNewUnverifiedForkchoice(
        new ForkchoiceEvent(null, null, this.finalizedHash));
    // Broadcast message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockPropagationManager.isRunning()).isFalse();
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }

  @Test
  public void shouldNotListenToNewBlockAnnouncementsWhenTTDReachedAndFinal() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    final RespondingSilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockMessage nextAnnouncement =
        NewBlockMessage.create(
            nextBlock,
            getFullBlockchain().getTotalDifficultyByHash(nextBlock.getHash()).get(),
            maxMessageSize);
    final Responder responder = RespondingSilPeer.blockchainResponder(getFullBlockchain());

    syncState.setReachedTerminalDifficulty(true);
    blockPropagationManager.onNewUnverifiedForkchoice(
        new ForkchoiceEvent(null, null, this.finalizedHash));
    // Broadcast message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, peer, nextAnnouncement);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(blockPropagationManager.isRunning()).isFalse();
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();
  }

  @Test
  public void shouldNotListenToBlockAddedEventsWhenTTDReachedAndFinal() {
    blockchainUtil.importFirstBlocks(2);

    blockPropagationManager.start();

    syncState.setReachedTerminalDifficulty(true);
    blockPropagationManager.onNewUnverifiedForkchoice(
        new ForkchoiceEvent(null, null, this.finalizedHash));
    blockchainUtil.importBlockAtIndex(2);

    assertThat(blockPropagationManager.isRunning()).isFalse();
    verifyNoInteractions(pendingBlocksManager);
  }

  @Test
  public void shouldRequestBlockFromOtherPeersIfFirstPeerFails() {
    blockchainUtil.importFirstBlocks(2);
    final Block nextBlock = blockchainUtil.getBlock(2);

    // Sanity check
    assertThat(blockchain.contains(nextBlock.getHash())).isFalse();

    blockPropagationManager.start();

    final RespondingSilPeer firstPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    SilProtocolManagerTestUtil.createPeer(silProtocolManager, 0);
    final NewBlockHashesMessage nextAnnouncement =
        NewBlockHashesMessage.create(
            Collections.singletonList(
                new NewBlockHashesMessage.NewBlockHash(
                    nextBlock.getHash(), nextBlock.getHeader().getNumber())));

    Mockito.reset(peerTaskExecutor);
    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(firstPeer.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT, Collections.emptyList()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenAnswer(
            new GetHeadersFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetBodiesFromPeerTask.class), Mockito.eq(firstPeer.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT, Collections.emptyList()));
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetBodiesFromPeerTask.class)))
        .thenAnswer(
            new GetBodiesFromPeerTaskExecutorAnswer(
                getFullBlockchain(), silProtocolManager.silContext().getSilPeers()));

    // Broadcast message
    SilProtocolManagerTestUtil.broadcastMessage(silProtocolManager, firstPeer, nextAnnouncement);

    Mockito.verify(peerTaskExecutor)
        .executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(firstPeer.getSilPeer()));
    Mockito.verify(peerTaskExecutor).execute(Mockito.any(GetHeadersFromPeerTask.class));
    Mockito.verify(peerTaskExecutor)
        .executeAgainstPeer(
            Mockito.any(GetBodiesFromPeerTask.class), Mockito.eq(firstPeer.getSilPeer()));
    Mockito.verify(peerTaskExecutor).execute(Mockito.any(GetBodiesFromPeerTask.class));
    Mockito.verifyNoMoreInteractions(peerTaskExecutor);

    verify(processingBlocksManager).addRequestedBlock(nextBlock.getHash());
    verify(processingBlocksManager).addImportingBlock(nextBlock.getHash());
    verify(processingBlocksManager).registerReceivedBlock(nextBlock);
    verify(processingBlocksManager).registerBlockImportDone(nextBlock.getHash());

    assertThat(blockchain.contains(nextBlock.getHash())).isTrue();
  }

  public abstract Blockchain getFullBlockchain();

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
