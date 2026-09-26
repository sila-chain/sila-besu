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
package org.hyperledger.besu.sila.sil.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.core.TransactionReceipt;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.RespondingEthPeer;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.exceptions.SilTaskException;
import org.hyperledger.besu.sila.sil.manager.exceptions.SilTaskException.FailureReason;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.task.SilTask;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetBlockHeaderFunctions;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class DetermineCommonAncestorTaskTest {

  private final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.TESTING_NETWORK;
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final int defaultHeaderRequestSize = 10;
  private MutableBlockchain localBlockchain;
  private Block localGenesisBlock;
  private SilProtocolManager silProtocolManager;
  private SilContext silContext;
  private ProtocolContext protocolContext;
  private PeerTaskExecutor peerTaskExecutor;

  @BeforeEach
  public void setup() {
    localGenesisBlock = blockDataGenerator.genesisBlock();
    localBlockchain = createInMemoryBlockchain(localGenesisBlock);
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(localBlockchain)
            .setWorldStateArchive(worldStateArchive)
            .setTransactionPool(mock(TransactionPool.class))
            .setEthereumWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    silContext = silProtocolManager.silContext();
    protocolContext =
        new ProtocolContext.Builder()
            .withBlockchain(localBlockchain)
            .withWorldStateArchive(worldStateArchive)
            .build();
  }

  @Test
  public void shouldFailIfPeerDisconnects() {
    final Block block = blockDataGenerator.nextBlock(localBlockchain.getChainHeadBlock());
    localBlockchain.appendBlock(block, blockDataGenerator.receipts(block));

    final RespondingEthPeer respondingEthPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    final SilTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            silContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);

    PeerTaskExecutorResult<List<BlockHeader>> taskResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Collections.emptyList()),
            PeerTaskExecutorResponseCode.PEER_DISCONNECTED,
            List.of(respondingEthPeer.getEthPeer()));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingEthPeer.getEthPeer())))
        .thenReturn(taskResult);

    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CompletableFuture<BlockHeader> future = task.run();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    // Disconnect the target peer
    respondingEthPeer.disconnect(DisconnectReason.CLIENT_QUITTING);

    assertThat(failure.get()).isNotNull();
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(SilTaskException.class);
    assertThat(((SilTaskException) error).reason()).isEqualTo(FailureReason.PEER_DISCONNECTED);
  }

  @Test
  public void shouldHandleEmptyResponses() {
    final Block block = blockDataGenerator.nextBlock(localBlockchain.getChainHeadBlock());
    localBlockchain.appendBlock(block, blockDataGenerator.receipts(block));

    final RespondingEthPeer respondingEthPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    final SilTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            silContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);

    PeerTaskExecutorResult<List<BlockHeader>> taskResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Collections.emptyList()),
            PeerTaskExecutorResponseCode.INVALID_RESPONSE,
            List.of(respondingEthPeer.getEthPeer()));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingEthPeer.getEthPeer())))
        .thenReturn(taskResult);

    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CompletableFuture<BlockHeader> future = task.run();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    assertThat(failure.get()).isNotNull();
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(RuntimeException.class);
    assertThat(((RuntimeException) error).getMessage())
        .isEqualTo("Peer failed to successfully return requested block headers");
  }

  @Test
  public void calculateSkipInterval() {
    final long maximumPossibleCommonAncestorNumber = 100;
    final long minimumPossibleCommonAncestorNumber = 0;
    final int headerRequestSize = 10;

    final long range = maximumPossibleCommonAncestorNumber - minimumPossibleCommonAncestorNumber;
    final int skipInterval =
        DetermineCommonAncestorTask.calculateSkipInterval(range, headerRequestSize);
    final int count = DetermineCommonAncestorTask.calculateCount((double) range, skipInterval);

    assertThat(count).isEqualTo(11);
    assertThat(skipInterval).isEqualTo(9);
  }

  @Test
  public void shouldIssueConsistentNumberOfRequestsToPeer() {
    final Blockchain remoteBlockchain = setupLocalAndRemoteChains(101, 101, 1);

    final RespondingEthPeer respondingEthPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    final SilTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            silContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingEthPeer.getEthPeer())))
        .thenAnswer(peerTaskExecutorResultAnswer(remoteBlockchain));

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    final CompletableFuture<BlockHeader> future = task.run();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(SilaMainnetBlockHeaderFunctions.createHash(localGenesisBlock.getHeader()));

    Mockito.verify(peerTaskExecutor, Mockito.times(2))
        .executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingEthPeer.getEthPeer()));
  }

  @Test
  public void shouldShortCircuitOnHeaderInInitialRequest() {
    final Blockchain remoteBlockchain = setupLocalAndRemoteChains(100, 100, 96);
    final BlockHeader commonHeader = localBlockchain.getBlockHeader(95).get();

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    final DetermineCommonAncestorTask task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            silContext,
            respondingEthPeer.getEthPeer(),
            10,
            metricsSystem);
    final DetermineCommonAncestorTask spy = spy(task);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingEthPeer.getEthPeer())))
        .thenAnswer(peerTaskExecutorResultAnswer(remoteBlockchain));

    // Execute task
    final CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(SilaMainnetBlockHeaderFunctions.createHash(commonHeader));

    Mockito.verify(peerTaskExecutor)
        .executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingEthPeer.getEthPeer()));
  }

  @Test
  public void returnsImmediatelyWhenThereIsNoWorkToDo() throws Exception {
    final RespondingEthPeer respondingEthPeer =
        spy(SilProtocolManagerTestUtil.createPeer(silProtocolManager));
    final SilPeer peer = spy(respondingEthPeer.getEthPeer());

    final SilTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            silContext,
            peer,
            defaultHeaderRequestSize,
            metricsSystem);

    final CompletableFuture<BlockHeader> result = task.run();
    assertThat(result).isCompletedWithValue(localGenesisBlock.getHeader());

    // Make sure we didn't ask for any headers
    verify(peer, times(0)).getHeadersByHash(any(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).getHeadersByNumber(anyLong(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).send(any());
  }

  /**
   * @param localBlockCount The number of local blocks to create. Highest block will be: {@code
   *     localBlockHeight} - 1.
   * @param remoteBlockCount The number of remote blocks to create. Highest block will be: {@code
   *     remoteBlockCount} - 1.
   * @param blocksInCommon The number of blocks shared between local and remote. If a common
   *     ancestor exists, its block number will be: {@code blocksInCommon} - 1
   * @return the test blockchain
   */
  private Blockchain setupLocalAndRemoteChains(
      final int localBlockCount, final int remoteBlockCount, final int blocksInCommon) {
    checkArgument(localBlockCount >= 1);
    checkArgument(remoteBlockCount >= 1);
    checkArgument(blocksInCommon >= 0);
    checkArgument(blocksInCommon <= Math.min(localBlockCount, remoteBlockCount));

    final Block remoteGenesis =
        (blocksInCommon > 0) ? localGenesisBlock : blockDataGenerator.genesisBlock();
    MutableBlockchain remoteChain = createInMemoryBlockchain(remoteGenesis);

    // Build common chain
    if (blocksInCommon > 1) {
      List<Block> commonBlocks =
          blockDataGenerator.blockSequence(remoteGenesis, blocksInCommon - 1);
      for (Block commonBlock : commonBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(commonBlock);
        localBlockchain.appendBlock(commonBlock, receipts);
        remoteChain.appendBlock(commonBlock, receipts);
      }
    }

    // Build divergent local blocks
    if (localBlockCount > blocksInCommon) {
      Block localChainHead = localBlockchain.getChainHeadBlock();
      final int currentHeight =
          Math.toIntExact(
              localBlockchain.getChainHeadBlockNumber() - BlockHeader.GENESIS_BLOCK_NUMBER + 1);
      List<Block> localBlocks =
          blockDataGenerator.blockSequence(localChainHead, localBlockCount - currentHeight);
      for (Block localBlock : localBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(localBlock);
        localBlockchain.appendBlock(localBlock, receipts);
      }
    }

    // Build divergent remote blocks
    if (remoteBlockCount > blocksInCommon) {
      Block remoteChainHead = remoteChain.getChainHeadBlock();
      final int currentHeight =
          Math.toIntExact(
              remoteChain.getChainHeadBlockNumber() - BlockHeader.GENESIS_BLOCK_NUMBER + 1);
      List<Block> remoteBlocks =
          blockDataGenerator.blockSequence(remoteChainHead, remoteBlockCount - currentHeight);
      for (Block remoteBlock : remoteBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(remoteBlock);
        remoteChain.appendBlock(remoteBlock, receipts);
      }
    }

    return remoteChain;
  }

  private Answer<PeerTaskExecutorResult<List<BlockHeader>>> peerTaskExecutorResultAnswer(
      final Blockchain remoteBlockchain) {
    return new Answer<PeerTaskExecutorResult<List<BlockHeader>>>() {
      @Override
      public PeerTaskExecutorResult<List<BlockHeader>> answer(
          final InvocationOnMock invocationOnMock) throws Throwable {
        GetHeadersFromPeerTask getHeadersTask =
            invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
        long blockNumber = getHeadersTask.getBlockNumber();
        int maxHeaders = getHeadersTask.getMaxHeaders();
        int skip = getHeadersTask.getSkip();

        List<BlockHeader> headers = new ArrayList<>();
        for (long i = blockNumber; i > blockNumber - (maxHeaders - 1) * (skip + 1); i -= skip + 1) {
          headers.add(remoteBlockchain.getBlockHeader(i).get());
        }

        return new PeerTaskExecutorResult<List<BlockHeader>>(
            Optional.of(headers), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());
      }
    };
  }
}
