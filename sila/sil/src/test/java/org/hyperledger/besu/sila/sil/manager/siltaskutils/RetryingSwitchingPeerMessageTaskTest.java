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
package org.hyperledger.besu.sila.sil.manager.siltaskutils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestUtil;
import org.hyperledger.besu.sila.sil.manager.RespondingSilPeer;
import org.hyperledger.besu.sila.sil.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.sila.sil.manager.task.SilTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

/**
 * Tests silTasks that request data from the network, and retry until all of the data is received.
 *
 * @param <T> The type of data being requested from the network
 */
public abstract class RetryingSwitchingPeerMessageTaskTest<T> extends RetryingMessageTaskTest<T> {
  protected Optional<SilPeer> responsivePeer = Optional.empty();

  @Override
  protected void assertResultMatchesExpectation(
      final T requestedData, final T response, final SilPeer respondingPeer) {
    assertThat(response).isEqualTo(requestedData);
    responsivePeer.ifPresent(rp -> assertThat(rp).isEqualByComparingTo(respondingPeer));
  }

  @Test
  public void completesWhenBestPeerEmptyAndSecondPeerIsResponsive()
      throws ExecutionException, InterruptedException {
    // Setup first unresponsive peer
    final RespondingSilPeer firstPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 10);

    // Setup second responsive peer
    final RespondingSilPeer secondPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 9);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final SilTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // First peer is not responsive
    firstPeer.respond(RespondingSilPeer.emptyResponder());
    // Second peer is responsive
    secondPeer.respondTimes(
        RespondingSilPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool),
        2);

    responsivePeer = Optional.of(secondPeer.getSilPeer());

    assertThat(future.isDone()).isTrue();
    assertResultMatchesExpectation(requestedData, future.get(), secondPeer.getSilPeer());
  }

  @Test
  public void completesWhenBestPeerTimeoutsAndSecondPeerIsResponsive()
      throws ExecutionException, InterruptedException {
    // Setup first unresponsive peer
    final RespondingSilPeer firstPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 10);

    // Setup second responsive peer
    final RespondingSilPeer secondPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 9);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final SilTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    // First peer timeouts
    peerCountToTimeout.set(1);
    firstPeer.respondTimes(
        RespondingSilPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool),
        2);
    // Second peer is responsive
    secondPeer.respondTimes(
        RespondingSilPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool),
        2);

    responsivePeer = Optional.of(secondPeer.getSilPeer());

    assertThat(future.isDone()).isTrue();
    assertResultMatchesExpectation(requestedData, future.get(), secondPeer.getSilPeer());
  }

  @Test
  public void failsWhenAllPeersFail() {
    // Setup first unresponsive peer
    final RespondingSilPeer firstPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 10);

    // Setup second unresponsive peer
    final RespondingSilPeer secondPeer =
        SilProtocolManagerTestUtil.createPeer(silProtocolManager, 9);

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final SilTask<T> task = createTask(requestedData);
    final CompletableFuture<T> future = task.run();

    for (int i = 0; i < maxRetries && !future.isDone(); i++) {
      // First peer is unresponsive
      firstPeer.respondWhile(RespondingSilPeer.emptyResponder(), firstPeer::hasOutstandingRequests);
      // Second peer is unresponsive
      secondPeer.respondWhile(
          RespondingSilPeer.emptyResponder(), secondPeer::hasOutstandingRequests);
    }

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get).hasCauseInstanceOf(MaxRetriesReachedException.class);

    // since we are below the max number of peers, no peer should be disconnected
    assertThat(firstPeer.getSilPeer().isDisconnected()).isFalse();
    assertThat(secondPeer.getSilPeer().isDisconnected()).isFalse();
  }

  @Test
  public void disconnectAPeerWhenAllPeersTried() {
    maxRetries = MAX_PEERS + 1;
    final int numPeers = MAX_PEERS;
    final List<RespondingSilPeer> respondingPeers = new ArrayList<>(numPeers);
    for (int i = 0; i < numPeers; i++) {
      respondingPeers.add(SilProtocolManagerTestUtil.createPeer(silProtocolManager, numPeers - i));
    }

    // Execute task and wait for response
    final T requestedData = generateDataToBeRequested();
    final SilTask<T> task = createTask(requestedData);
    task.run();

    respondingPeers.forEach(
        respondingPeer ->
            respondingPeer.respondWhile(
                RespondingSilPeer.emptyResponder(), respondingPeer::hasOutstandingRequests));

    assertThat(respondingPeers.get(numPeers - 1).getSilPeer().isDisconnected()).isTrue();
  }
}
