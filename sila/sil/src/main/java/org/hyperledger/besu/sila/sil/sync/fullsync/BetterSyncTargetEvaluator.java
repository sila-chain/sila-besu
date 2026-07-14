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
package org.hyperledger.besu.sila.sil.sync.fullsync;

import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;

import java.util.Optional;

public class BetterSyncTargetEvaluator {

  private final SynchronizerConfiguration config;
  private final SilPeers silPeers;

  public BetterSyncTargetEvaluator(
      final SynchronizerConfiguration config, final SilPeers silPeers) {
    this.config = config;
    this.silPeers = silPeers;
  }

  public boolean shouldSwitchSyncTarget(final SilPeer currentSyncTarget) {
    final ChainState currentPeerChainState = currentSyncTarget.chainState();
    final Optional<SilPeer> maybeBestPeer = silPeers.bestPeer();

    return maybeBestPeer
        .map(
            bestPeer -> {
              if (silPeers
                      .getBestPeerComparator()
                      .compare(
                          SilPeerImmutableAttributes.from(bestPeer),
                          SilPeerImmutableAttributes.from(currentSyncTarget))
                  <= 0) {
                // Our current target is better or equal to the best peer
                return false;
              }
              // Require some threshold to be exceeded before switching targets to keep some
              // stability when multiple peers are in range of each other
              final ChainState bestPeerChainState = bestPeer.chainState();
              final Difficulty tdDifference =
                  bestPeerChainState
                      .getEstimatedTotalDifficulty()
                      .subtract(currentPeerChainState.getBestBlock().getTotalDifficulty());
              if (tdDifference.compareTo(config.getDownloaderChangeTargetThresholdByTd()) > 0) {
                return true;
              }
              final long heightDifference =
                  bestPeerChainState.getEstimatedHeight()
                      - currentPeerChainState.getEstimatedHeight();
              return heightDifference > config.getDownloaderChangeTargetThresholdByHeight();
            })
        .orElse(false);
  }
}
