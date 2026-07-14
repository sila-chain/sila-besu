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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.snap.GetAccountRangeFromPeerTask;
import org.hyperledger.besu.sila.sil.manager.task.AbstractPeerTask;
import org.hyperledger.besu.sila.sil.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapServerChecker {

  private static final Logger LOG = LoggerFactory.getLogger(SnapServerChecker.class);

  private final SilContext silContext;
  private final MetricsSystem metricsSystem;

  public SnapServerChecker(final SilContext silContext, final MetricsSystem metricsSystem) {
    this.silContext = silContext;
    this.metricsSystem = metricsSystem;
  }

  public static void createAndSetSnapServerChecker(
      final SilContext silContext, final MetricsSystem metricsSystem) {
    final SnapServerChecker checker = new SnapServerChecker(silContext, metricsSystem);
    silContext.getSilPeers().setSnapServerChecker(checker);
  }

  public CompletableFuture<Boolean> check(final SilPeer peer, final BlockHeader peersHeadHeader) {
    LOG.atTrace()
        .setMessage("Checking whether peer {} is a snap server ...")
        .addArgument(peer::getLoggableId)
        .log();
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<AccountRangeMessage.AccountRangeData>>
        snapServerCheckCompletableFuture = getAccountRangeFromPeer(peer, peersHeadHeader);
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    snapServerCheckCompletableFuture.whenComplete(
        (peerResult, error) -> {
          if (peerResult != null) {
            if (!peerResult.getResult().accounts().isEmpty()
                || !peerResult.getResult().proofs().isEmpty()) {
              LOG.atTrace()
                  .setMessage("Peer {} is a snap server.")
                  .addArgument(peer::getLoggableId)
                  .log();
              future.complete(true);
            } else {
              LOG.atTrace()
                  .setMessage("Peer {} is not a snap server.")
                  .addArgument(peer::getLoggableId)
                  .log();
              future.complete(false);
            }
          }
        });
    return future;
  }

  public CompletableFuture<AbstractPeerTask.PeerTaskResult<AccountRangeMessage.AccountRangeData>>
      getAccountRangeFromPeer(final SilPeer peer, final BlockHeader header) {
    return GetAccountRangeFromPeerTask.forAccountRange(
            silContext,
            Bytes32.wrap(Hash.ZERO.getBytes()),
            Bytes32.wrap(Hash.ZERO.getBytes()),
            header,
            metricsSystem)
        .assignPeer(peer)
        .run();
  }
}
