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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.task.AbstractPeerTask.PeerTaskResult;

import java.util.Optional;

public abstract class AbstractPeerTask<R> extends AbstractEthTask<PeerTaskResult<R>> {
  protected Optional<SilPeer> assignedPeer = Optional.empty();
  protected final SilContext silContext;

  protected AbstractPeerTask(final SilContext silContext, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.silContext = silContext;
  }

  public AbstractPeerTask<R> assignPeer(final SilPeer peer) {
    assignedPeer = Optional.of(peer);
    return this;
  }

  public static class PeerTaskResult<T> {
    private final SilPeer peer;
    private final T result;

    public PeerTaskResult(final SilPeer peer, final T result) {
      this.peer = peer;
      this.result = result;
    }

    public SilPeer getPeer() {
      return peer;
    }

    public T getResult() {
      return result;
    }
  }
}
