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

import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.sila.sil.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.sila.sil.manager.exceptions.PeerBreachedProtocolException;
import org.hyperledger.besu.sila.sil.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task that will retry a fixed number of times before completing the associated CompletableFuture
 * exceptionally with a new {@link MaxRetriesReachedException}. If the future returned from {@link
 * #executePeerTask(Optional)} is complete with a non-empty list the retry counter is reset.
 *
 * @param <T> The type as a typed list that the peer task can get partial or full results in.
 */
public abstract class AbstractRetryingPeerTask<T> extends AbstractSilTask<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractRetryingPeerTask.class);
  private final SilContext silContext;
  private final int maxRetries;
  private final Predicate<T> isEmptyResponse;
  private final MetricsSystem metricsSystem;
  private int retryCount = 0;
  private Optional<SilPeer> assignedPeer = Optional.empty();

  /**
   * @param silContext The context of the current Sil network we are attached to.
   * @param maxRetries Maximum number of retries to accept before completing exceptionally.
   * @param isEmptyResponse Test if the response received was empty.
   * @param metricsSystem The metrics system used to measure task.
   */
  protected AbstractRetryingPeerTask(
      final SilContext silContext,
      final int maxRetries,
      final Predicate<T> isEmptyResponse,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.silContext = silContext;
    this.maxRetries = maxRetries;
    this.isEmptyResponse = isEmptyResponse;
    this.metricsSystem = metricsSystem;
  }

  /**
   * Assign the peer to be used for the task.
   *
   * @param peer The peer to assign to the task.
   * @return True if the peer was assigned, false otherwise.
   */
  public boolean assignPeer(final SilPeer peer) {
    if (isSuitablePeer(SilPeerImmutableAttributes.from(peer))) {
      assignedPeer = Optional.of(peer);
      return true;
    } else {
      assignedPeer = Optional.empty();
      return false;
    }
  }

  public Optional<SilPeer> getAssignedPeer() {
    return assignedPeer;
  }

  @Override
  protected void executeTask() {
    if (result.isDone()) {
      // Return if task is done
      return;
    }
    if (retryCount >= maxRetries) {
      result.completeExceptionally(new MaxRetriesReachedException());
      return;
    }

    retryCount += 1;
    executePeerTask(assignedPeer)
        .whenComplete(
            (peerResult, error) -> {
              if (error != null) {
                handleTaskError(error);
              } else {
                // If we get a partial success, reset the retry counter.
                if (!isEmptyResponse.test(peerResult)) {
                  retryCount = 0;
                }
                executeTaskTimed();
              }
            });
  }

  protected abstract CompletableFuture<T> executePeerTask(Optional<SilPeer> assignedPeer);

  protected void handleTaskError(final Throwable error) {
    final Throwable cause = ExceptionUtils.rootCause(error);
    if (!isRetryableError(cause)) {
      // Complete exceptionally
      result.completeExceptionally(cause);
      return;
    }

    if (cause instanceof NoAvailablePeersException) {
      LOG.debug(
          "No useful peer found, wait max 5 seconds for new peer to connect: current peers {}",
          silContext.getSilPeers().peerCount());

      executeSubTask(
          () ->
              silContext
                  .getSilPeers()
                  .waitForPeer(this::isSuitablePeer)
                  .orTimeout(5, TimeUnit.SECONDS)
                  // execute the task again
                  .whenComplete((r, t) -> executeTaskTimed()));
      return;
    }

    LOG.atDebug()
        .setMessage("Retrying after recoverable failure from peer task {}: {}")
        .addArgument(this.getClass().getSimpleName())
        .addArgument(cause.getMessage())
        .log();
    // Wait before retrying on failure
    executeSubTask(
        () ->
            silContext
                .getScheduler()
                .scheduleFutureTask(this::executeTaskTimed, Duration.ofSeconds(1)));
  }

  protected boolean isRetryableError(final Throwable error) {
    return error instanceof TimeoutException || (!assignedPeer.isPresent() && isPeerFailure(error));
  }

  protected boolean isPeerFailure(final Throwable error) {
    return error instanceof PeerBreachedProtocolException
        || error instanceof PeerDisconnectedException
        || error instanceof NoAvailablePeersException;
  }

  protected SilContext getSilContext() {
    return silContext;
  }

  protected MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public int getRetryCount() {
    return retryCount;
  }

  protected void resetRetryCount() {
    retryCount = 0;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  protected boolean isSuitablePeer(final SilPeerImmutableAttributes peer) {
    return true;
  }
}
