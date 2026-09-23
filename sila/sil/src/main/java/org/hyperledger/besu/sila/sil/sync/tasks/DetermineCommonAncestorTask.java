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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.sila.sil.manager.task.AbstractSilTask;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.util.BlockchainUtil;

import java.util.List;
import java.util.OptionalInt;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the common ancestor with the given peer. It is assumed that the peer will at least share
 * the same genesis block with this node. Running this task against a peer with a non-matching
 * genesis block will result in undefined behavior: the task may complete exceptionally or in some
 * cases this node's genesis block will be returned.
 */
public class DetermineCommonAncestorTask extends AbstractSilTask<BlockHeader> {
  private static final Logger LOG = LoggerFactory.getLogger(DetermineCommonAncestorTask.class);
  private final SilContext silContext;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final SilPeer peer;
  private final int headerRequestSize;

  private long maximumPossibleCommonAncestorNumber;
  private long minimumPossibleCommonAncestorNumber;
  private BlockHeader commonAncestorCandidate;
  private boolean initialQuery = true;

  private DetermineCommonAncestorTask(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final SilPeer peer,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.silContext = silContext;
    this.protocolContext = protocolContext;
    this.peer = peer;
    this.headerRequestSize = headerRequestSize;

    maximumPossibleCommonAncestorNumber =
        Math.min(
            protocolContext.getBlockchain().getChainHeadBlockNumber(),
            peer.chainState().getEstimatedHeight());
    minimumPossibleCommonAncestorNumber = BlockHeader.GENESIS_BLOCK_NUMBER;
    commonAncestorCandidate =
        protocolContext.getBlockchain().getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get();
  }

  public static DetermineCommonAncestorTask create(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final SilPeer peer,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    return new DetermineCommonAncestorTask(
        protocolSchedule, protocolContext, silContext, peer, headerRequestSize, metricsSystem);
  }

  @Override
  protected void executeTask() {
    if (maximumPossibleCommonAncestorNumber == minimumPossibleCommonAncestorNumber) {
      // Bingo, we found our common ancestor.
      result.complete(commonAncestorCandidate);
      return;
    }
    if (maximumPossibleCommonAncestorNumber < BlockHeader.GENESIS_BLOCK_NUMBER
        && !result.isDone()) {
      result.completeExceptionally(new IllegalStateException("No common ancestor."));
      return;
    }

    silContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              do {
                PeerTaskExecutorResult<List<BlockHeader>> taskResult =
                    requestHeadersUsingPeerTaskSystem();
                if (taskResult.responseCode() == PeerTaskExecutorResponseCode.PEER_DISCONNECTED) {
                  result.completeExceptionally(new PeerDisconnectedException(peer));
                  continue;
                } else if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
                    || taskResult.result().isEmpty()) {
                  result.completeExceptionally(
                      new RuntimeException(
                          "Peer failed to successfully return requested block headers"));
                  continue;
                }
                taskResult.silPeers().stream()
                    .findAny()
                    .ifPresent((unused) -> taskResult.result().get().getFirst());
                processHeaders(taskResult.result().get());
                if (maximumPossibleCommonAncestorNumber == minimumPossibleCommonAncestorNumber) {
                  // Bingo, we found our common ancestor.
                  result.complete(commonAncestorCandidate);
                } else if (maximumPossibleCommonAncestorNumber < BlockHeader.GENESIS_BLOCK_NUMBER
                    && !result.isDone()) {
                  result.completeExceptionally(new IllegalStateException("No common ancestor."));
                }
              } while (!result.isDone());
            });
  }

  PeerTaskExecutorResult<List<BlockHeader>> requestHeadersUsingPeerTaskSystem() {
    final long range = maximumPossibleCommonAncestorNumber - minimumPossibleCommonAncestorNumber;
    final int skipInterval = initialQuery ? 0 : calculateSkipInterval(range, headerRequestSize);
    final int count =
        initialQuery ? headerRequestSize : calculateCount((double) range, skipInterval);
    LOG.debug(
        "Searching for common ancestor with {} between {} and {}",
        peer,
        minimumPossibleCommonAncestorNumber,
        maximumPossibleCommonAncestorNumber);
    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(
            maximumPossibleCommonAncestorNumber,
            count,
            skipInterval,
            Direction.REVERSE,
            protocolSchedule);
    return silContext.getPeerTaskExecutor().executeAgainstPeer(task, peer);
  }

  /**
   * In the case where the remote chain contains 100 blocks, the initial count work out to 11, and
   * the skip interval would be 9. This would yield the headers (0, 10, 20, 30, 40, 50, 60, 70, 80,
   * 90, 100).
   */
  @VisibleForTesting
  static int calculateSkipInterval(final long range, final int headerRequestSize) {
    return Math.max(0, Math.toIntExact(range / (headerRequestSize - 1) - 1) - 1);
  }

  @VisibleForTesting
  static int calculateCount(final double range, final int skipInterval) {
    return Math.toIntExact((long) Math.ceil(range / (skipInterval + 1)) + 1);
  }

  private void processHeaders(final List<BlockHeader> headers) {
    initialQuery = false;
    if (headers.isEmpty()) {
      // Nothing to do
      return;
    }

    final OptionalInt maybeAncestorNumber =
        BlockchainUtil.findHighestKnownBlockIndex(protocolContext.getBlockchain(), headers, false);

    // Means the insertion point is in the next header request.
    if (!maybeAncestorNumber.isPresent()) {
      maximumPossibleCommonAncestorNumber = headers.get(headers.size() - 1).getNumber() - 1L;
      return;
    }
    final int ancestorNumber = maybeAncestorNumber.getAsInt();
    commonAncestorCandidate = headers.get(ancestorNumber);

    if (ancestorNumber - 1 >= 0) {
      maximumPossibleCommonAncestorNumber = headers.get(ancestorNumber - 1).getNumber() - 1L;
    }
    minimumPossibleCommonAncestorNumber = headers.get(ancestorNumber).getNumber();
  }
}
