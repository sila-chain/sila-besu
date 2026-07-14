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

import org.hyperledger.besu.sila.sil.manager.task.AbstractSilTask;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.concurrent.CountDownLatch;

public class MockSilTask extends AbstractSilTask<Object> {

  private final CountDownLatch startedLatch = new CountDownLatch(1);
  private final CountDownLatch countdown;

  MockSilTask(final int count) {
    super(new NoOpMetricsSystem());
    countdown = new CountDownLatch(count);
  }

  MockSilTask() {
    this(0);
  }

  @Override
  protected void executeTask() {
    startedLatch.countDown();
    try {
      countdown.await();
    } catch (final InterruptedException ignore) {
      // ignore
    }
  }

  boolean hasBeenStarted() {
    return startedLatch.getCount() == 0;
  }

  void complete() {
    if (hasBeenStarted() && countdown.getCount() == 0) {
      result.complete(null);
    }
  }

  void fail() {
    result.completeExceptionally(new RuntimeException("Failure forced for testing"));
  }
}
