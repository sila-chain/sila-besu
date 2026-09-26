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

import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.sync.state.SyncTarget;
import org.hyperledger.besu.sila.sil.sync.tasks.DetermineCommonAncestorTask;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSyncTargetManager {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractSyncTargetManager.class);

  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final SilContext silContext;
  private final MetricsSystem metricsSystem;

  protected AbstractSyncTargetManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final MetricsSystem metricsSystem) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.silContext = silContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<SyncTarget> findSyncTarget() {
    if (isCancelled()) {
      return completedFuture(null);
    }
    return selectBestAvailableSyncTarget()
        .thenCompose(
            maybeBestPeer -> {
              if (maybeBestPeer.isPresent()) {
                final SilPeer bestPeer = maybeBestPeer.get();
                return DetermineCommonAncestorTask.create(
                        protocolSchedule,
                        protocolContext,
                        silContext,
                        bestPeer,
                        config.getDownloaderHeaderRequestSize(),
                        metricsSystem)
                    .run()
                    .handle(
                        (result, error) -> {
                          if (error != null) {
                            LOG.debug("Failed to find common ancestor", error);
                          }
                          return result;
                        })
                    .thenCompose(
                        (target) -> {
                          if (target == null) {
                            return waitForPeerAndThenSetSyncTarget();
                          }
                          final SyncTarget syncTarget = new SyncTarget(bestPeer, target);
                          LOG.debug(
                              "Found common ancestor with peer {} at block {}",
                              bestPeer,
                              target.getNumber());
                          return completedFuture(syncTarget);
                        })
                    .thenCompose(
                        syncTarget ->
                            finalizeSelectedSyncTarget(syncTarget)
                                .map(CompletableFuture::completedFuture)
                                .orElseGet(this::waitForPeerAndThenSetSyncTarget));
              } else {
                return waitForPeerAndThenSetSyncTarget();
              }
            });
  }

  public synchronized void cancel() {
    cancelled.set(true);
  }

  protected Optional<SyncTarget> finalizeSelectedSyncTarget(final SyncTarget syncTarget) {
    return Optional.of(syncTarget);
  }

  protected abstract CompletableFuture<Optional<SilPeer>> selectBestAvailableSyncTarget();

  private CompletableFuture<SyncTarget> waitForPeerAndThenSetSyncTarget() {
    return silContext
        .getScheduler()
        .scheduleFutureTask(
            () ->
                silContext
                    .getEthPeers()
                    .waitForPeer((peer) -> true)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .handle((ignored, ignored2) -> null)
                    .thenCompose((r) -> findSyncTarget()),
            Duration.ofSeconds(5));
  }

  private boolean isCancelled() {
    return cancelled.get();
  }

  public abstract boolean shouldContinueDownloading();
}
