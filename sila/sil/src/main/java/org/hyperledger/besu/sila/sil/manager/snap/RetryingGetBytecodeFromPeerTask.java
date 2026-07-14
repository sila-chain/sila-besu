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
import org.apache.tuweni.bytes.Bytes32;

public class RetryingGetBytecodeFromPeerTask
    extends AbstractRetryingSwitchingPeerTask<Map<Bytes32, Bytes>> {

  public static final int MAX_RETRIES = 4;

  private final SilContext silContext;
  private final List<Bytes32> codeHashes;
  private final BlockHeader blockHeader;
  private final MetricsSystem metricsSystem;

  private RetryingGetBytecodeFromPeerTask(
      final SilContext silContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(silContext, metricsSystem, Map::isEmpty, MAX_RETRIES);
    this.silContext = silContext;
    this.codeHashes = codeHashes;
    this.blockHeader = blockHeader;
    this.metricsSystem = metricsSystem;
  }

  public static SilTask<Map<Bytes32, Bytes>> forByteCode(
      final SilContext silContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new RetryingGetBytecodeFromPeerTask(silContext, codeHashes, blockHeader, metricsSystem);
  }

  @Override
  protected CompletableFuture<Map<Bytes32, Bytes>> executeTaskOnCurrentPeer(final SilPeer peer) {
    final GetBytecodeFromPeerTask task =
        GetBytecodeFromPeerTask.forBytecode(silContext, codeHashes, blockHeader, metricsSystem);
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
