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
package org.hyperledger.besu.sila.sil.manager.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hyperledger.besu.sila.referencetests.ReferenceTestBlockchain.generateTestBlockHash;

import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.sila.sil.manager.MockPeerConnection;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.siltaskutils.PeerMessageTaskTest;
import org.hyperledger.besu.sila.sil.messages.BlockHeadersMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GetHeadersFromPeerByHashTaskTest extends PeerMessageTaskTest<List<BlockHeader>> {

  @Override
  protected void assertPartialResultMatchesExpectation(
      final List<BlockHeader> requestedData, final List<BlockHeader> partialResponse) {
    assertThat(partialResponse.size()).isLessThanOrEqualTo(requestedData.size());
    assertThat(partialResponse.size()).isGreaterThan(0);
    for (final BlockHeader header : partialResponse) {
      assertThat(requestedData).contains(header);
    }
  }

  @Override
  protected List<BlockHeader> generateDataToBeRequested() {
    final int count = 3;
    final List<BlockHeader> requestedHeaders = new ArrayList<>(count);
    for (long i = 0; i < count; i++) {
      requestedHeaders.add(blockchain.getBlockHeader(5 + i).get());
    }
    return requestedHeaders;
  }

  @Override
  protected SilTask<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> createTask(
      final List<BlockHeader> requestedData) {
    final BlockHeader firstHeader = requestedData.getFirst();
    return GetHeadersFromPeerByHashTask.startingAtHash(
        protocolSchedule,
        silContext,
        firstHeader.getHash(),
        firstHeader.getNumber(),
        requestedData.size(),
        metricsSystem);
  }

  @Test
  public void getHeadersFromHashNoSkip() {
    getHeadersFromHash(0, false);
  }

  @Test
  public void getHeadersFromHashNoSkipReversed() {
    getHeadersFromHash(0, true);
  }

  @Test
  public void getHeadersFromHashWithSkip() {
    getHeadersFromHash(2, false);
  }

  @Test
  public void getHeadersFromHashWithSkipReversed() {
    getHeadersFromHash(2, true);
  }

  private void getHeadersFromHash(final int skip, final boolean reverse) {
    // Setup a responsive peer
    final RespondingSilPeer.Responder responder = RespondingSilPeer.blockchainResponder(blockchain);
    final RespondingSilPeer respondingPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager);

    // Set up parameters and calculated expected response
    final long startNumber = reverse ? blockchain.getChainHeadBlockNumber() - 2 : 2;
    final int delta = (skip + 1) * (reverse ? -1 : 1);
    final int count = 4;
    final List<BlockHeader> expectedHeaders = new ArrayList<>(count);
    for (long i = 0; i < count; i++) {
      expectedHeaders.add(blockchain.getBlockHeader(startNumber + delta * i).get());
    }

    // Execute task and wait for response
    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            silContext,
            blockchain.getBlockHashByNumber(startNumber).get(),
            startNumber,
            count,
            skip,
            reverse,
            metricsSystem);
    final AtomicReference<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> actualResult =
        new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future = task.run();
    respondingPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertThat(actualResult.get().getPeer()).isEqualTo(respondingPeer.getSilPeer());
    assertThat(actualResult.get().getResult()).isEqualTo(expectedHeaders);
  }

  @Test
  public void checkThatSequentialHeadersFormingAChainWorks() {
    final int startNumber = 1;

    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(0)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(block1.getHash()).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);

    final SilPeer peer = createPeer();

    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            silContext,
            block1.getHash(),
            startNumber,
            2,
            0,
            false,
            metricsSystem);
    final Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders).isPresent();
    final List<BlockHeader> blockHeaders = optionalBlockHeaders.get();
    MatcherAssert.assertThat(blockHeaders, hasSize(2));
    assertThat(peer.chainState().getEstimatedHeight()).isEqualTo(2);
    assertThat(peer.isDisconnected()).isFalse();
  }

  @Test
  public void checkThatSequentialHeadersNotFormingAChainFails() {
    final int startNumber = 1;
    final BlockHeader block1 =
        new BlockHeaderTestFixture().number(1).parentHash(generateTestBlockHash(0)).buildHeader();
    final BlockHeader block2 =
        new BlockHeaderTestFixture().number(2).parentHash(generateTestBlockHash(1)).buildHeader();
    final List<BlockHeader> headers = Arrays.asList(block1, block2);

    final SilPeer peer = createPeer();

    final AbstractGetHeadersFromPeerTask task =
        new GetHeadersFromPeerByHashTask(
            protocolSchedule,
            silContext,
            block1.getHash(),
            startNumber,
            2,
            0,
            false,
            metricsSystem);
    final Optional<List<BlockHeader>> optionalBlockHeaders =
        task.processResponse(false, BlockHeadersMessage.create(headers), peer);
    assertThat(optionalBlockHeaders).isNotNull();
    assertThat(optionalBlockHeaders).isEmpty();
    assertThat(peer.isDisconnected()).isTrue();
    assertThat(((MockPeerConnection) peer.getConnection()).getDisconnectReason().get())
        .isEqualTo(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_NON_SEQUENTIAL_HEADERS);
  }
}
