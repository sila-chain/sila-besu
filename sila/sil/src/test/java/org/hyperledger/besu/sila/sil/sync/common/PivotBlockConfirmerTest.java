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
package org.hyperledger.besu.sila.sil.sync.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.sync.common.PivotBlockConfirmer.ContestedPivotBlockException;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicSilScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class PivotBlockConfirmerTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private SilProtocolManager silProtocolManager;
  private MutableBlockchain blockchain;
  private TransactionPool transactionPool;
  private ProtocolSchedule protocolSchedule;
  private PeerTaskExecutor peerTaskExecutor;

  static class PivotBlockConfirmerTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setSilScheduler(new DeterministicSilScheduler(timeout::get))
            .setWorldStateArchive(blockchainSetupUtil.getWorldArchive())
            .setTransactionPool(transactionPool)
            .setSilaWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
  }

  private PivotBlockConfirmer createPivotBlockConfirmer(final int peersToQuery) {
    return spy(
        new PivotBlockConfirmer(
            protocolSchedule, silProtocolManager.silContext(), PIVOT_BLOCK_NUMBER, peersToQuery));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void completeSuccessfully(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2);

    final RespondingSilPeer respondingPeerA =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    final RespondingSilPeer respondingPeerB =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getSilPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getSilPeer())));

    // Execute task
    final CompletableFuture<SnapSyncProcessState> future = pivotBlockConfirmer.confirmPivotBlock();

    future.join();
    assertThat(future)
        .isCompletedWithValue(
            new SnapSyncProcessState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get(), false));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void peerTimesOut(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2);

    RespondingSilPeer peerA = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    RespondingSilPeer peerB = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);
    RespondingSilPeer peerC = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerA.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerA.getSilPeer())));
    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerB.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT, Collections.emptyList()));
    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peerC.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peerC.getSilPeer())));

    // Execute task
    final CompletableFuture<SnapSyncProcessState> future = pivotBlockConfirmer.confirmPivotBlock();

    assertThat(future)
        .isCompletedWithValue(
            new SnapSyncProcessState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get(), false));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void headerMismatch(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2);

    final RespondingSilPeer respondingPeerA =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    final RespondingSilPeer respondingPeerB =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerA.getSilPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getSilPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        new BlockHeaderTestFixture()
                            .number(PIVOT_BLOCK_NUMBER)
                            .extraData(Bytes.of(1))
                            .buildHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(respondingPeerB.getSilPeer())));

    // Execute task and wait for response
    final CompletableFuture<SnapSyncProcessState> future = pivotBlockConfirmer.confirmPivotBlock();

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(ContestedPivotBlockException.class);
  }
}
