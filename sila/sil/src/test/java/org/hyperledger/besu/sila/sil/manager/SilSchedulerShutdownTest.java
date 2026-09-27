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
package org.hyperledger.besu.sila.sil.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SilSchedulerShutdownTest {

  private SilScheduler silScheduler;
  private ExecutorService syncWorkerExecutor;
  private ScheduledExecutorService scheduledExecutor;
  private ExecutorService txWorkerExecutor;
  private ExecutorService servicesExecutor;
  private ExecutorService computationExecutor;
  private ExecutorService blockCreationExecutor;

  @BeforeEach
  public void setup() {
    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    syncWorkerExecutor = Executors.newSingleThreadExecutor();
    txWorkerExecutor = Executors.newSingleThreadExecutor();
    servicesExecutor = Executors.newSingleThreadExecutor();
    computationExecutor = Executors.newSingleThreadExecutor();
    blockCreationExecutor = Executors.newSingleThreadExecutor();
    silScheduler =
        new SilScheduler(
            syncWorkerExecutor,
            scheduledExecutor,
            txWorkerExecutor,
            servicesExecutor,
            computationExecutor,
            blockCreationExecutor);
  }

  @Test
  public void shutdown_syncWorkerShutsDown() throws InterruptedException {
    final MockEthTask task1 = new MockEthTask(1);
    final MockEthTask task2 = new MockEthTask();

    silScheduler.scheduleSyncWorkerTask(task1::executeTask);
    silScheduler.scheduleSyncWorkerTask(task2::executeTask);
    silScheduler.stop();

    assertThat(syncWorkerExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(syncWorkerExecutor.isShutdown()).isTrue();
    assertThat(syncWorkerExecutor.isTerminated()).isTrue();
    assertThat(task2.hasBeenStarted()).isFalse();
  }

  @Test
  public void shutdown_scheduledWorkerShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    silScheduler.scheduleFutureTask(task::executeTask, Duration.ofMillis(0));
    silScheduler.stop();

    assertThat(scheduledExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(scheduledExecutor.isShutdown()).isTrue();
    assertThat(scheduledExecutor.isTerminated()).isTrue();
  }

  @Test
  public void shutdown_txWorkerShutsDown() throws InterruptedException {
    final MockEthTask task1 = new MockEthTask(1);
    final MockEthTask task2 = new MockEthTask();

    silScheduler.scheduleTxWorkerTask(task1::executeTask);
    silScheduler.scheduleTxWorkerTask(task2::executeTask);
    silScheduler.stop();

    assertThat(txWorkerExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(txWorkerExecutor.isShutdown()).isTrue();
    assertThat(txWorkerExecutor.isTerminated()).isTrue();
    assertThat(task2.hasBeenStarted()).isFalse();
  }

  @Test
  public void shutdown_servicesShutsDown() throws InterruptedException {
    final MockEthTask task1 = new MockEthTask(1);
    final MockEthTask task2 = new MockEthTask();

    silScheduler.scheduleServiceTask(task1);
    silScheduler.scheduleServiceTask(task2);
    silScheduler.stop();

    assertThat(servicesExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(servicesExecutor.isShutdown()).isTrue();
    assertThat(servicesExecutor.isTerminated()).isTrue();
    assertThat(task2.hasBeenStarted()).isFalse();
  }

  @Test
  public void shutdown_computationShutsDown() throws InterruptedException {
    final MockEthTask task1 = new MockEthTask(1);
    final MockEthTask task2 = new MockEthTask();

    silScheduler.scheduleComputationTask(
        () -> {
          task1.executeTask();
          return Integer.MAX_VALUE;
        });
    silScheduler.scheduleComputationTask(
        () -> {
          task2.executeTask();
          return Integer.MAX_VALUE;
        });
    silScheduler.stop();

    assertThat(computationExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(computationExecutor.isShutdown()).isTrue();
    assertThat(computationExecutor.isTerminated()).isTrue();
    assertThat(task2.hasBeenStarted()).isFalse();
  }

  @Test
  public void shutdown_blockCreationShutsDown() throws InterruptedException {
    final MockEthTask task1 = new MockEthTask(1);
    final MockEthTask task2 = new MockEthTask();

    silScheduler.scheduleBlockCreationTask(1L, task1::executeTask);
    silScheduler.scheduleBlockCreationTask(2L, task2::executeTask);
    silScheduler.stop();

    assertThat(blockCreationExecutor.isShutdown()).isTrue();

    silScheduler.awaitStop();

    assertThat(blockCreationExecutor.isShutdown()).isTrue();
    assertThat(blockCreationExecutor.isTerminated()).isTrue();
    assertThat(task2.hasBeenStarted()).isFalse();
  }
}
