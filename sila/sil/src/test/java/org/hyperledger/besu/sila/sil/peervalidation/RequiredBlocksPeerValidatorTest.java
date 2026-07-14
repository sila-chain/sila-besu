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
package org.hyperledger.besu.sila.sil.peervalidation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.sila.core.ProtocolScheduleFixture;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class RequiredBlocksPeerValidatorTest extends AbstractPeerBlockValidatorTest {

  @Override
  AbstractPeerBlockValidator createValidator(
      final PeerTaskExecutor peerTaskExecutor, final long blockNumber, final long buffer) {
    return new RequiredBlocksPeerValidator(
        ProtocolScheduleFixture.TESTING_NETWORK, peerTaskExecutor, blockNumber, Hash.ZERO, buffer);
  }

  @Test
  public void validatePeer_responsivePeerWithRequiredBlock() {
    final SilProtocolManager silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(ProtocolScheduleFixture.TESTING_NETWORK)
            .build();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long requiredBlockNumber = 500;
    final Block requiredBlock =
        gen.block(BlockOptions.create().setBlockNumber(requiredBlockNumber));

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            ProtocolScheduleFixture.TESTING_NETWORK,
            peerTaskExecutor,
            requiredBlockNumber,
            requiredBlock.getHash(),
            0);

    final SilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, requiredBlockNumber).getSilPeer();

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(requiredBlock.getHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer)));

    final CompletableFuture<Boolean> result =
        validator.validatePeer(silProtocolManager.silContext(), peer);

    ArgumentCaptor<GetHeadersFromPeerTask> getHeadersTaskCaptor =
        ArgumentCaptor.forClass(GetHeadersFromPeerTask.class);
    Mockito.verify(peerTaskExecutor)
        .executeAgainstPeer(getHeadersTaskCaptor.capture(), Mockito.eq(peer));
    assertThat(getHeadersTaskCaptor.getValue().getBlockNumber()).isEqualTo(requiredBlockNumber);
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  public void validatePeer_responsivePeerWithBadRequiredBlock() {
    final SilProtocolManager silProtocolManager = SilProtocolManagerTestBuilder.builder().build();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long requiredBlockNumber = 500;
    final Block requiredBlock =
        gen.block(BlockOptions.create().setBlockNumber(requiredBlockNumber));

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            ProtocolScheduleFixture.TESTING_NETWORK,
            peerTaskExecutor,
            requiredBlockNumber,
            Hash.ZERO,
            0);

    final SilPeer peer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, requiredBlockNumber).getSilPeer();

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(requiredBlock.getHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer)));

    final CompletableFuture<Boolean> result =
        validator.validatePeer(silProtocolManager.silContext(), peer);

    ArgumentCaptor<GetHeadersFromPeerTask> getHeadersTaskCaptor =
        ArgumentCaptor.forClass(GetHeadersFromPeerTask.class);
    Mockito.verify(peerTaskExecutor)
        .executeAgainstPeer(getHeadersTaskCaptor.capture(), Mockito.eq(peer));
    assertThat(getHeadersTaskCaptor.getValue().getBlockNumber()).isEqualTo(requiredBlockNumber);
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void validatePeer_responsivePeerDoesNotHaveBlockWhenPastForkHeight() {
    final SilProtocolManager silProtocolManager = SilProtocolManagerTestBuilder.builder().build();

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            ProtocolScheduleFixture.TESTING_NETWORK, peerTaskExecutor, 1, Hash.ZERO);

    final SilPeer peer = SilProtocolManagerTestUtil.createPeer(silProtocolManager, 1).getSilPeer();

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(Collections.emptyList()),
                PeerTaskExecutorResponseCode.SUCCESS,
                List.of(peer)));

    final CompletableFuture<Boolean> result =
        validator.validatePeer(silProtocolManager.silContext(), peer);

    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }
}
