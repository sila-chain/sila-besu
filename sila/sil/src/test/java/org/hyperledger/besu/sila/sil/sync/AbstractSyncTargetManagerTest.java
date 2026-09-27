/*
 * Copyright contributors to Besu.
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AbstractSyncTargetManagerTest {

  private SilScheduler silScheduler;

  @AfterEach
  public void tearDown() {
    if (silScheduler != null) {
      silScheduler.stop();
    }
  }

  // Regression test for besu-sil/besu#10864. FullSyncTargetManager's retry loop could deadlock
  // forever when the sole connected peer's outstanding-request budget was momentarily exhausted
  // (e.g. by BlockPropagationManager's backward parent walk) at the exact moment
  // waitForPeerAndThenSetSyncTarget() ran: SilPeers.waitForPeer() only wakes on a *new* peer
  // connecting, never on an existing peer's capacity freeing back up, and had no timeout - so if
  // no new peer ever connects, the retry chain hangs silently forever. This simulates "no new peer
  // will ever connect" by stubbing waitForPeer() to return a future that never completes on its
  // own, and asserts findSyncTarget() still gets retried thanks to the orTimeout guard.
  @Test
  public void findSyncTargetRetriesEvenWhenWaitForPeerNeverCompletes() {
    silScheduler = new SilScheduler(1, 1, 1, 1, new NoOpMetricsSystem());
    final SilPeers silPeers = mock(SilPeers.class);
    when(silPeers.waitForPeer(any())).thenAnswer(invocation -> new CompletableFuture<SilPeer>());

    final SilContext silContext = mock(SilContext.class);
    when(silContext.getScheduler()).thenReturn(silScheduler);
    when(silContext.getEthPeers()).thenReturn(silPeers);

    final AtomicInteger selectionAttempts = new AtomicInteger();
    final AbstractSyncTargetManager syncTargetManager =
        new AbstractSyncTargetManager(
            mock(SynchronizerConfiguration.class),
            mock(ProtocolSchedule.class),
            mock(ProtocolContext.class),
            silContext,
            new NoOpMetricsSystem()) {
          @Override
          protected CompletableFuture<Optional<SilPeer>> selectBestAvailableSyncTarget() {
            selectionAttempts.incrementAndGet();
            return completedFuture(Optional.empty());
          }

          @Override
          public boolean shouldContinueDownloading() {
            return true;
          }
        };

    syncTargetManager.findSyncTarget();

    // The first attempt happens synchronously; a second attempt only happens if the retry
    // chain survives waitForPeer() never completing on its own - proving the orTimeout guard
    // added in AbstractSyncTargetManager.waitForPeerAndThenSetSyncTarget() actually unsticks it.
    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(selectionAttempts.get()).isGreaterThanOrEqualTo(2));
  }
}
