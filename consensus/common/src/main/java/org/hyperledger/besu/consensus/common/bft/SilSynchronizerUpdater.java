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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Sil synchronizer updater. */
public class SilSynchronizerUpdater implements SynchronizerUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(SilSynchronizerUpdater.class);
  private final SilPeers silPeers;

  /**
   * Instantiates a new Sil synchronizer updater.
   *
   * @param silPeers the sil peers
   */
  public SilSynchronizerUpdater(final SilPeers silPeers) {
    this.silPeers = silPeers;
  }

  @Override
  public void updatePeerChainState(
      final long knownBlockNumber, final PeerConnection peerConnection) {
    final SilPeer silPeer = silPeers.peer(peerConnection);
    if (silPeer == null) {
      LOG.debug("Received message from a peer with no corresponding SilPeer.");
      return;
    }
    silPeer.chainState().updateHeightEstimate(knownBlockNumber);
  }
}
