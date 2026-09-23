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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.manager.MockPeerConnection;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.sila.worldstate.WorldStateStorageCoordinator;

import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SnapProtocolManagerTest {

  @Mock private WorldStateStorageCoordinator worldStateStorageCoordinator;
  @Mock private SnapSyncConfiguration snapConfig;
  @Mock private SilPeers silPeers;
  @Mock private SilMessages snapMessages;
  @Mock private ProtocolContext protocolContext;
  @Mock private Synchronizer synchronizer;
  @Mock private SilScheduler silScheduler;
  @Mock private SilPeer silPeer;

  private SnapProtocolManager snapProtocolManager;

  @BeforeEach
  void setUp() {
    when(snapConfig.isSnapServerEnabled()).thenReturn(false);
    snapProtocolManager = createSnapProtocolManager();
  }

  @Test
  void advertisesOnlySnap1WhenSnap2IsDisabled() {
    when(snapConfig.isSnap2Enabled()).thenReturn(false);

    snapProtocolManager = createSnapProtocolManager();

    assertThat(snapProtocolManager.getSupportedCapabilities()).containsExactly(SnapProtocol.SNAP1);
  }

  @Test
  void advertisesSnap2WhenSnap2IsEnabled() {
    when(snapConfig.isSnap2Enabled()).thenReturn(true);

    snapProtocolManager = createSnapProtocolManager();

    assertThat(snapProtocolManager.getSupportedCapabilities())
        .containsExactly(SnapProtocol.SNAP1, SnapProtocol.SNAP2);
  }

  @Test
  void disconnectsPeerOnDecompressionFailure() {
    final MockPeerConnection peerConnection =
        new MockPeerConnection(
            new HashSet<>(Collections.singletonList(SnapProtocol.SNAP1)), (cap, msg, conn) -> {});
    when(silPeers.peer(peerConnection)).thenReturn(silPeer);
    when(silPeer.validateReceivedMessage(any(), any())).thenReturn(true);

    // Create a RawMessage with invalid compressed data that will throw FramingException
    final RawMessage badMessage = new RawMessage(0x00, new byte[] {0x01, 0x02, 0x03});
    snapProtocolManager.processMessage(
        SnapProtocol.SNAP1, new DefaultMessage(peerConnection, badMessage));

    assertThat(peerConnection.isDisconnected()).isFalse();
    // silPeer (mock) receives the disconnect call
    org.mockito.Mockito.verify(silPeer)
        .disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
  }

  private SnapProtocolManager createSnapProtocolManager() {
    return new SnapProtocolManager(
        worldStateStorageCoordinator,
        snapConfig,
        silPeers,
        snapMessages,
        silScheduler,
        protocolContext,
        synchronizer);
  }
}
