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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.RespondingEthPeer;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.task.SilTask;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.ValidationPolicy;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.sila.sil.transactions.BlobCache;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolFactory;
import org.hyperledger.besu.sila.silaMainnet.BlockHeaderValidator;
import org.hyperledger.besu.sila.silaMainnet.HeaderValidationMode;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpec;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;
import org.hyperledger.besu.testutil.TestClock;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DownloadHeaderSequenceTaskTest {

  private final ValidationPolicy validationPolicy = () -> HeaderValidationMode.DETACHED_ONLY;

  protected static Blockchain blockchain;
  protected static ProtocolSchedule protocolSchedule;
  protected static ProtocolContext protocolContext;
  protected static MetricsSystem metricsSystem = new NoOpMetricsSystem();
  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;
  protected SilPeers silPeers;
  protected TransactionPool transactionPool;
  protected PeerTaskExecutor peerTaskExecutor;
  protected AtomicBoolean peersDoTimeout;
  protected AtomicInteger peerCountToTimeout;

  @BeforeAll
  public static void setup() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    assertThat(blockchainSetupUtil.getMaxBlockNumber()).isGreaterThanOrEqualTo(20L);
  }

  @BeforeEach
  public void setupTest() {
    protocolContext.getBadBlockManager().reset();
    peersDoTimeout = new AtomicBoolean(false);
    peerCountToTimeout = new AtomicInteger(0);
    silPeers =
        spy(
            new SilPeers(
                () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()),
                TestClock.fixed(),
                metricsSystem,
                SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
                Collections.emptyList(),
                Bytes.random(64),
                5,
                5,
                false,
                SyncMode.SNAP,
                new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList())));

    final SilMessages silMessages = new SilMessages();
    final SilScheduler silScheduler =
        new DeterministicEthScheduler(
            () -> peerCountToTimeout.getAndDecrement() > 0 || peersDoTimeout.get());
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    silContext = new SilContext(silPeers, silMessages, silScheduler, peerTaskExecutor);
    final SyncState syncState = new SyncState(blockchain, silContext.getEthPeers());
    final SilProtocolConfiguration silProtocolConfiguration = SilProtocolConfiguration.DEFAULT;
    transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            silContext,
            TestClock.system(ZoneId.systemDefault()),
            metricsSystem,
            syncState,
            TransactionPoolConfiguration.DEFAULT,
            silProtocolConfiguration,
            new BlobCache(),
            MiningConfiguration.newDefault());
    transactionPool.setEnabled();

    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setEthScheduler(silScheduler)
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(silProtocolConfiguration)
            .setEthPeers(silPeers)
            .setEthMessages(silMessages)
            .setEthContext(silContext)
            .build();
  }

  protected List<BlockHeader> generateDataToBeRequested() {
    final List<BlockHeader> requestedHeaders = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final long blockNumber = 10 + i;
      final BlockHeader header = blockchain.getBlockHeader(blockNumber).get();
      requestedHeaders.add(header);
    }
    return requestedHeaders;
  }

  protected SilTask<List<BlockHeader>> createTask(final List<BlockHeader> requestedData) {
    final BlockHeader lastHeader = requestedData.getLast();
    final BlockHeader referenceHeader = blockchain.getBlockHeader(lastHeader.getNumber() + 1).get();
    return DownloadHeaderSequenceTask.endingAtHeader(
        protocolSchedule,
        protocolContext,
        silContext,
        referenceHeader,
        requestedData.size(),
        validationPolicy,
        metricsSystem);
  }

  @Test
  public void failsWhenPeerReturnsOnlyReferenceHeader() {
    RespondingEthPeer respondingEthPeer = SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(referenceHeader)),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingEthPeer.getEthPeer())));
    final SilTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            silContext,
            referenceHeader,
            10,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void failsWhenPeerReturnsOnlySubsetOfHeaders() {
    final RespondingEthPeer respondingPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    // Execute task and wait for response
    final BlockHeader referenceHeader = blockchain.getChainHeadHeader();
    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        referenceHeader,
                        blockchain.getBlockHeader(referenceHeader.getNumber() - 1).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeer.getEthPeer())));

    final SilTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolSchedule,
            protocolContext,
            silContext,
            referenceHeader,
            10,
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);
    assertNoBadBlocks();
  }

  @Test
  public void marksBadBlockWhenHeaderValidationFails() {
    final RespondingEthPeer respondingPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);
    // Set up a chain with an invalid block
    final int blockCount = 5;
    final long startBlock = blockchain.getChainHeadBlockNumber() - blockCount;
    final List<Block> chain = getBlockSequence(startBlock, blockCount);
    final Block badBlock = chain.get(2);
    ProtocolSchedule protocolScheduleSpy = setupHeaderValidationToFail(badBlock.getHeader());

    Mockito.when(peerTaskExecutor.execute(Mockito.any(GetHeadersFromPeerTask.class)))
        .then(
            (invocationOnMock) -> {
              GetHeadersFromPeerTask task =
                  invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
              List<BlockHeader> headers = new ArrayList<>();
              for (long i = task.getBlockNumber();
                  i > task.getBlockNumber() - (long) task.getMaxHeaders() * (task.getSkip() + 1);
                  i -= task.getSkip() + 1) {
                Optional<BlockHeader> header = blockchain.getBlockHeader(i);
                if (header.isPresent()) {
                  headers.add(header.get());
                } else {
                  break;
                }
              }
              return new PeerTaskExecutorResult<List<BlockHeader>>(
                  Optional.of(headers),
                  PeerTaskExecutorResponseCode.SUCCESS,
                  List.of(respondingPeer.getEthPeer()));
            });

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetBodiesFromPeerTask.class), Mockito.any(SilPeer.class)))
        .thenAnswer(
            (invocationOnMock) -> {
              GetBodiesFromPeerTask task =
                  invocationOnMock.getArgument(0, GetBodiesFromPeerTask.class);
              SilPeer peer = invocationOnMock.getArgument(1, SilPeer.class);
              List<Block> blocks =
                  task.getBlockHeaders().stream()
                      .map(
                          (blockHeader) ->
                              new Block(
                                  blockHeader,
                                  blockchain.getBlockBody(blockHeader.getBlockHash()).get()))
                      .toList();
              return new PeerTaskExecutorResult<List<Block>>(
                  Optional.of(blocks), PeerTaskExecutorResponseCode.SUCCESS, List.of(peer));
            });

    // Execute the task
    final BlockHeader referenceHeader = chain.get(blockCount - 1).getHeader();
    final SilTask<List<BlockHeader>> task =
        DownloadHeaderSequenceTask.endingAtHeader(
            protocolScheduleSpy,
            protocolContext,
            silContext,
            referenceHeader,
            blockCount - 1, // The reference header is not included in this count
            validationPolicy,
            metricsSystem);
    final CompletableFuture<List<BlockHeader>> future = task.run();

    //    final RespondingEthPeer.Responder fullResponder = getFullResponder();
    //    respondingPeer.respondWhile(fullResponder, () -> !future.isDone());

    // Check that the future completed exceptionally
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidBlockException.class)
        .hasMessageContaining("Header failed validation");

    // Check bad blocks
    assertBadBlock(badBlock);
  }

  private List<Block> getBlockSequence(final long firstBlockNumber, final int blockCount) {
    final List<Block> blocks = new ArrayList<>(blockCount);
    for (int i = 0; i < blockCount; i++) {
      final Block block = blockchain.getBlockByNumber(firstBlockNumber + i).get();
      blocks.add(block);
    }

    return blocks;
  }

  private ProtocolSchedule setupHeaderValidationToFail(final BlockHeader badHeader) {
    ProtocolSchedule protocolScheduleSpy = spy(protocolSchedule);
    ProtocolSpec failingProtocolSpec = spy(protocolSchedule.getByBlockHeader(badHeader));
    BlockHeaderValidator failingValidator = mock(BlockHeaderValidator.class);
    when(failingValidator.validateHeader(eq(badHeader), any(), any(), any())).thenReturn(false);
    when(failingProtocolSpec.getBlockHeaderValidator()).thenReturn(failingValidator);
    doAnswer(
            invocation -> {
              BlockHeader header = invocation.getArgument(0);
              if (header.getNumber() == badHeader.getNumber()) {
                return failingProtocolSpec;
              } else {
                return invocation.callRealMethod();
              }
            })
        .when(protocolScheduleSpy)
        .getByBlockHeader(any(BlockHeader.class));

    return protocolScheduleSpy;
  }

  private void assertBadBlock(final Block badBlock) {
    BadBlockManager badBlockManager = protocolContext.getBadBlockManager();
    assertThat(badBlockManager.getBadBlocks()).containsExactly(badBlock);
    assertThat(badBlockManager.getBadHeaders()).isEmpty();
  }

  protected void assertNoBadBlocks() {
    BadBlockManager badBlockManager = protocolContext.getBadBlockManager();
    assertThat(badBlockManager.getBadBlocks().size()).isEqualTo(0);
    assertThat(badBlockManager.getBadHeaders().size()).isEqualTo(0);
  }
}
