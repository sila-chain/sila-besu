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
package org.hyperledger.besu.sila.sil.manager.peertask;

import org.hyperledger.besu.sila.p2p.peers.PeerId;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Selects the SilPeers for the PeerTaskExecutor */
public interface PeerSelector {

  /**
   * Gets a peer matching the supplied filter
   *
   * @param filter a Predicate\<SilPeerImmutableAttributes\> matching desirable peers
   * @return a peer matching the supplied conditions
   */
  Optional<SilPeer> getPeer(final Predicate<SilPeerImmutableAttributes> filter);

  /**
   * Waits for a peer matching the supplied filter
   *
   * @param filter a Predicate\<SilPeerImmutableAttributes\> matching desirable peers
   * @return a CompletableFuture into which a peer will be placed
   */
  CompletableFuture<SilPeer> waitForPeer(final Predicate<SilPeerImmutableAttributes> filter);

  /**
   * Attempts to get the SilPeer identified by peerId
   *
   * @param peerId the peerId of the desired SilPeer
   * @return An Optional\<SilPeer\> containing the SilPeer identified by peerId if present in the
   *     PeerSelector, or empty otherwise
   */
  Optional<SilPeer> getPeerByPeerId(PeerId peerId);
}
