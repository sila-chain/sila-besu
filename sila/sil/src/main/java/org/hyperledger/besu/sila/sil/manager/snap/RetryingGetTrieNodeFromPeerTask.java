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
package org.hyperledger.besu.sila.sil.manager.snap;

import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.task.AbstractRetryingSwitchingPeerTask;
import org.hyperledger.besu.sila.sil.manager.task.SilTask;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;

public class RetryingGetTrieNodeFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<Map<Bytes, Bytes>> {

  public static final int MAX_RETRIES = 4;

  private final SilContext silContext;
  private final Map<Bytes, List<Bytes>> paths;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetTrieNodeFromPeerTask(
      final SilContext silContext,
      final Map<Bytes, List<Bytes>> paths,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(silContext, metricsSystem, Map::isEmpty, MAX_RETRIES);
    this.silContext = silContext;
    this.paths = paths;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static SilTask<Map<Bytes, Bytes>> forTrieNodes(
      final SilContext silContext,
      final Map<Bytes, List<Bytes>> paths,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetTrieNodeFromPeerTask(silContext, paths, blockHeader, metricsSystem);
  }

  @Override
  protected CompletableFuture<Map<Bytes, Bytes>> executeTaskOnCurrentPeer(final SilPeer peer) {
    final GetTrieNodeFromPeerTask task =
        GetTrieNodeFromPeerTask.forTrieNodes(silContext, paths, blockHeader, metricsSystem);
    return executeSubTask(task::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult.getResult());
              return peerResult.getResult();
            });
  }

  @Override
  protected boolean isSuitablePeer(final SilPeerImmutableAttributes peer) {
    return peer.isServingSnap();
  }
}
