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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SilSynchronizerUpdaterTest {

  @Mock private SilPeers silPeers;
  @Mock private SilPeer silPeer;

  @Mock private ChainState chainState;

  @Test
  public void silPeerIsMissingResultInNoUpdate() {
    when(silPeers.peer(any(PeerConnection.class))).thenReturn(null);

    final SilSynchronizerUpdater updater = new SilSynchronizerUpdater(silPeers);

    updater.updatePeerChainState(1, mock(PeerConnection.class));

    verifyNoInteractions(silPeer);
  }

  @Test
  public void chainStateUpdateIsAttemptedIfSilPeerExists() {
    when(silPeers.peer(any(PeerConnection.class))).thenReturn(silPeer);
    when(silPeer.chainState()).thenReturn(chainState);

    final SilSynchronizerUpdater updater = new SilSynchronizerUpdater(silPeers);

    final long suppliedChainHeight = 6L;
    updater.updatePeerChainState(suppliedChainHeight, mock(PeerConnection.class));
    verify(chainState, times(1)).updateHeightEstimate(eq(suppliedChainHeight));
  }
}
