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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockBody;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.messages.NewBlockMessage;
import org.hyperledger.besu.util.number.ByteUnits;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class BlockBroadcasterTest {

  final int maxMessageSize = 10 * ByteUnits.MEGABYTE;

  @Test
  public void blockPropagationUnitTest() throws PeerConnection.PeerNotConnected {
    final SilPeer silPeer = mock(SilPeer.class);
    final SilPeerImmutableAttributes silPeerImmutableAttributes =
        mock(SilPeerImmutableAttributes.class);
    when(silPeerImmutableAttributes.silPeer()).thenReturn(silPeer);
    final SilPeers silPeers = mock(SilPeers.class);
    when(silPeers.streamAvailablePeers()).thenReturn(Stream.of(silPeerImmutableAttributes));

    final SilContext silContext = mock(SilContext.class);
    when(silContext.getSilPeers()).thenReturn(silPeers);

    final BlockBroadcaster blockBroadcaster = new BlockBroadcaster(silContext, maxMessageSize);
    final Block block = generateBlock();
    final NewBlockMessage newBlockMessage =
        NewBlockMessage.create(block, block.getHeader().getDifficulty(), maxMessageSize);

    blockBroadcaster.propagate(block, Difficulty.ZERO);

    verify(silPeer, times(1)).send(newBlockMessage);
  }

  @Test
  public void blockPropagationUnitTestSeenUnseen() throws PeerConnection.PeerNotConnected {
    final SilPeer silPeer0 = mock(SilPeer.class);
    final SilPeerImmutableAttributes silPeerImmutableAttributes0 =
        mock(SilPeerImmutableAttributes.class);
    when(silPeerImmutableAttributes0.silPeer()).thenReturn(silPeer0);
    when(silPeer0.hasSeenBlock(any())).thenReturn(true);

    final SilPeer silPeer1 = mock(SilPeer.class);
    final SilPeerImmutableAttributes silPeerImmutableAttributes1 =
        mock(SilPeerImmutableAttributes.class);
    when(silPeerImmutableAttributes1.silPeer()).thenReturn(silPeer1);

    final SilPeers silPeers = mock(SilPeers.class);
    when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(silPeerImmutableAttributes0, silPeerImmutableAttributes1));

    final SilContext silContext = mock(SilContext.class);
    when(silContext.getSilPeers()).thenReturn(silPeers);

    final BlockBroadcaster blockBroadcaster = new BlockBroadcaster(silContext, maxMessageSize);
    final Block block = generateBlock();
    final NewBlockMessage newBlockMessage =
        NewBlockMessage.create(block, block.getHeader().getDifficulty(), maxMessageSize);

    blockBroadcaster.propagate(block, Difficulty.ZERO);

    verify(silPeer0, never()).send(newBlockMessage);
    verify(silPeer1, times(1)).send(newBlockMessage);
  }

  private Block generateBlock() {
    final BlockBody body = new BlockBody(Collections.emptyList(), Collections.emptyList());
    return new Block(new BlockHeaderTestFixture().buildHeader(), body);
  }
}
