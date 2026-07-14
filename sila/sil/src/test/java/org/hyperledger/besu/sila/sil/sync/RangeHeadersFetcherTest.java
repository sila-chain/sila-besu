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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer.Responder;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.sync.range.RangeHeadersFetcher;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class RangeHeadersFetcherTest {

  private static final int SEGMENT_SIZE = 5;
  private static Blockchain blockchain;
  private static ProtocolSchedule protocolSchedule;
  private static ProtocolContext protocolContext;
  private static TransactionPool transactionPool;

  private static final Answer<PeerTaskExecutorResult<List<BlockHeader>>> executeAgainstPeerAnswer =
      (invocationOnMock) -> {
        List<BlockHeader> resultList = new ArrayList<>();
        GetHeadersFromPeerTask task = invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
        for (long i = task.getBlockNumber();
            i < task.getBlockNumber() + task.getMaxHeaders() * (task.getSkip() + 1);
            i += task.getSkip() + 1) {
          Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(i);
          if (blockHeader.isPresent()) {
            resultList.add(blockHeader.get());
          } else {
            break;
          }
        }
        return new PeerTaskExecutorResult<List<BlockHeader>>(
            Optional.of(resultList), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());
      };

  private SilProtocolManager silProtocolManager;
  private PeerTaskExecutor peerTaskExecutor;
  private Responder responder;
  private RespondingSilPeer respondingPeer;

  @BeforeAll
  public static void setUpClass() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
  }

  @BeforeEach
  public void setUpTest() {
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    responder =
        RespondingSilPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    respondingPeer =
        SilProtocolManagerTestUtil.createPeer(
            silProtocolManager, blockchain.getChainHeadBlockNumber());
  }

  @Test
  public void shouldRequestHeadersFromPeerAndExcludeExistingHeader() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher();

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingPeer.getSilPeer())))
        .thenAnswer(executeAgainstPeerAnswer);

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(1));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11), header(16)));
  }

  @Test
  public void shouldNotRequestHeadersBeyondTargetWhenTargetIsMultipleOfSegmentSize() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher(header(11));

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingPeer.getSilPeer())))
        .thenAnswer(executeAgainstPeerAnswer);

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(1));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11)));
  }

  @Test
  public void shouldNotRequestHeadersBeyondTargetWhenTargetIsNotAMultipleOfSegmentSize() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher(header(15));

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingPeer.getSilPeer())))
        .thenAnswer(executeAgainstPeerAnswer);

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(1));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(asList(header(6), header(11)));
  }

  @Test
  public void shouldReturnOnlyTargetHeaderWhenLastHeaderIsTheRangeBeforeTarget() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher(header(15));

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(11));

    assertThat(result).isCompletedWithValue(singletonList(header(15)));
  }

  @Test
  public void shouldReturnEmptyListWhenLastHeaderIsTarget() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher(header(15));

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(15));
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void shouldReturnEmptyListWhenLastHeaderIsAfterTarget() {
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher(header(15));

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(respondingPeer.getSilPeer(), header(16));
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void nextRangeShouldEndAtChainHeadWhenNextRangeHeaderIsAfterHead() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher();

    assertThat(
            rangeHeaderFetcher.nextRangeEndsAtChainHead(
                respondingPeer.getSilPeer(), header(remoteChainHeight - SEGMENT_SIZE + 1)))
        .isTrue();
  }

  @Test
  public void nextRangeShouldNotEndAtChainHeadWhenAFinalRangeHeaderIsSpecified() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final RangeHeadersFetcher rangeHeaderFetcher =
        createRangeHeaderFetcher(header(remoteChainHeight));

    assertThat(
            rangeHeaderFetcher.nextRangeEndsAtChainHead(
                respondingPeer.getSilPeer(), header(remoteChainHeight - SEGMENT_SIZE + 1)))
        .isFalse();
  }

  @Test
  public void shouldReturnRemoteChainHeadWhenNextRangeHeaderIsTheRemoteHead() {
    final long remoteChainHeight = blockchain.getChainHeadBlockNumber();
    final RangeHeadersFetcher rangeHeaderFetcher = createRangeHeaderFetcher();

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(respondingPeer.getSilPeer())))
        .thenAnswer(executeAgainstPeerAnswer);

    assertThat(
            rangeHeaderFetcher.nextRangeEndsAtChainHead(
                respondingPeer.getSilPeer(), header(remoteChainHeight - SEGMENT_SIZE)))
        .isFalse();

    final CompletableFuture<List<BlockHeader>> result =
        rangeHeaderFetcher.getNextRangeHeaders(
            respondingPeer.getSilPeer(), header(remoteChainHeight - SEGMENT_SIZE));

    respondingPeer.respond(responder);

    assertThat(result).isCompletedWithValue(singletonList(header(remoteChainHeight)));
  }

  private RangeHeadersFetcher createRangeHeaderFetcher() {
    final SilContext silContext = silProtocolManager.silContext();
    return new RangeHeadersFetcher(
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(SEGMENT_SIZE)
            .downloaderHeadersRequestSize(3)
            .build(),
        protocolSchedule,
        silContext);
  }

  private RangeHeadersFetcher createRangeHeaderFetcher(final BlockHeader targetHeader) {
    final SilContext silContext = silProtocolManager.silContext();
    return new RangeHeadersFetcher(
        SynchronizerConfiguration.builder()
            .downloaderChainSegmentSize(SEGMENT_SIZE)
            .downloaderHeadersRequestSize(3)
            .build(),
        protocolSchedule,
        silContext,
        new SnapSyncProcessState(targetHeader, false));
  }

  private BlockHeader header(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).get();
  }
}
