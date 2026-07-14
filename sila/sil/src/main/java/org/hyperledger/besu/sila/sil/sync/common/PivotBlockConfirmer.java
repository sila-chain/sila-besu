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

import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This task will query {@code numberOfPeersToQuery} peers for a particular block number. If any
 * peers disagree on the block at this number, the task fails with a {@code
 * ContestedPivotBlockException}. The task will succeed only if {@code numberOfPeersToQuery}
 * distinct peers all return matching block headers for the specified block number.
 */
class PivotBlockConfirmer {
  private static final Logger LOG = LoggerFactory.getLogger(PivotBlockConfirmer.class);

  private final SilContext silContext;
  private final ProtocolSchedule protocolSchedule;

  // The number of peers we need to query to confirm our pivot block
  private final int numberOfPeersToQuery;
  // The current pivot block number, gets pushed back if peers disagree on the pivot block
  private final long pivotBlockNumber;

  private final CompletableFuture<SnapSyncProcessState> result = new CompletableFuture<>();
  private final Collection<CompletableFuture<?>> runningQueries = new ConcurrentLinkedQueue<>();
  private final Map<BlockHeader, AtomicInteger> pivotBlockVotes = new ConcurrentHashMap<>();
  private final Set<SilPeer> peersUsed = Collections.synchronizedSet(new HashSet<>());

  private final AtomicBoolean isStarted = new AtomicBoolean(false);
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  PivotBlockConfirmer(
      final ProtocolSchedule protocolSchedule,
      final SilContext silContext,
      final long pivotBlockNumber,
      final int numberOfPeersToQuery) {
    this.protocolSchedule = protocolSchedule;
    this.silContext = silContext;
    this.pivotBlockNumber = pivotBlockNumber;
    this.numberOfPeersToQuery = numberOfPeersToQuery;
  }

  public CompletableFuture<SnapSyncProcessState> confirmPivotBlock() {
    if (isStarted.compareAndSet(false, true)) {
      LOG.info(
          "Confirm pivot block {} with at least {} peers.", pivotBlockNumber, numberOfPeersToQuery);
      queryPeers(pivotBlockNumber);
    }

    return result;
  }

  private void queryPeers(final long blockNumber) {
    synchronized (runningQueries) {
      for (SilPeer silPeer :
          silContext
              .getSilPeers()
              .streamBestPeers()
              .map(SilPeerImmutableAttributes::silPeer)
              .toList()
              .subList(0, numberOfPeersToQuery)) {
        peersUsed.add(silPeer);
        final CompletableFuture<?> query =
            executePivotQuery(blockNumber, silPeer).whenComplete(this::processReceivedHeader);
        runningQueries.add(query);
      }
    }
  }

  private void processReceivedHeader(final BlockHeader blockHeader, final Throwable throwable) {
    if (throwable != null) {
      cancelQueries();
      LOG.error("Encountered error while requesting pivot block header", throwable);
      result.completeExceptionally(throwable);
      return;
    }

    // Update votes
    pivotBlockVotes.putIfAbsent(blockHeader, new AtomicInteger(0));
    final int votes = pivotBlockVotes.get(blockHeader).incrementAndGet();

    if (pivotBlockVotes.keySet().size() > 1) {
      // We've received conflicting signals for the target block, confirmation has failed
      cancelQueries();
      LOG.info(
          "Failed to confirm pivot block {}. Received conflicting headers: {}",
          pivotBlockNumber,
          votesToString());
      result.completeExceptionally(
          new ContestedPivotBlockException(pivotBlockNumber, votesToString()));
    } else if (votes >= numberOfPeersToQuery) {
      // We've received the required number of votes and have selected our pivot block
      LOG.info("Confirmed pivot block at {}: {}", pivotBlockNumber, blockHeader.getHash());
      result.complete(new SnapSyncProcessState(blockHeader, false));
    } else {
      LOG.info(
          "Received {} confirmation(s) for pivot block header {}: {}",
          votes,
          pivotBlockNumber,
          blockHeader.getHash());
    }
  }

  private void cancelQueries() {
    synchronized (runningQueries) {
      isCancelled.set(true);
      runningQueries.forEach(f -> f.cancel(true));
    }
  }

  private String votesToString() {
    return pivotBlockVotes.entrySet().stream()
        .map(e -> e.getKey().getHash() + " (" + e.getValue().get() + ")")
        .collect(Collectors.joining(","));
  }

  private CompletableFuture<BlockHeader> executePivotQuery(
      final long blockNumber, final SilPeer silPeer) {
    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(
            blockNumber,
            1,
            0,
            GetHeadersFromPeerTask.Direction.FORWARD,
            silContext.getSilPeers().getMaxPeers(),
            protocolSchedule);
    if (isCancelled.get()) {
      return CompletableFuture.failedFuture(
          new CancellationException("Pivot block confirmation has been cancelled"));
    }

    return silContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              PeerTaskExecutorResult<List<BlockHeader>> taskResult =
                  silContext.getPeerTaskExecutor().executeAgainstPeer(task, silPeer);
              if (taskResult.responseCode() == PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR) {
                // something is probably wrong with the request, so we won't retry as below
                return CompletableFuture.failedFuture(
                    new RuntimeException("Unexpected internal issue"));
              } else if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
                  || taskResult.result().isEmpty()) {
                // recursively call executePivotQuery to retry with a different peer.
                try {
                  return executePivotQuery(
                      blockNumber,
                      silContext
                          .getSilPeers()
                          .waitForPeer((p) -> !peersUsed.contains(p.silPeer()))
                          .get());
                } catch (InterruptedException | ExecutionException e) {
                  return CompletableFuture.failedFuture(e);
                }
              }
              return CompletableFuture.completedFuture(taskResult.result().get().getFirst());
            });
  }

  public static class ContestedPivotBlockException extends RuntimeException {

    private final long blockNumber;

    public ContestedPivotBlockException(final long blockNumber, final String message) {
      super(message);
      this.blockNumber = blockNumber;
    }

    public long getBlockNumber() {
      return blockNumber;
    }
  }
}
