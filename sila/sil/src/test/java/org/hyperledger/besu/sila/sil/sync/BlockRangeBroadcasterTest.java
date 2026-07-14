/*
 * Copyright contributors to Besu.
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.sil.manager.ChainState;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilMessage;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.PeerReputation;
import org.hyperledger.besu.sila.sil.messages.BlockRangeUpdateMessage;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlockRangeBroadcasterTest {

  @Mock private Blockchain blockchain;
  @Mock private SilContext silContext;
  @Mock private SilPeers silPeers;
  @Mock private SilPeer silPeerWithSupport;
  @Mock private SilPeer silPeerWithoutSupport;

  private BlockRangeBroadcaster blockRangeBroadcaster;

  @BeforeEach
  public void setup() {
    when(silContext.getSilMessages()).thenReturn(mock(SilMessages.class));
    when(silContext.getScheduler()).thenReturn(mock(SilScheduler.class));
    blockRangeBroadcaster = spy(new BlockRangeBroadcaster(silContext, blockchain));
  }

  @Test
  public void shouldBroadcastBlockRange() throws PeerConnection.PeerNotConnected {
    setupPeers(silPeerWithSupport);
    when(silPeerWithSupport.hasSupportForMessage(SilProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(true);
    broadcastBlockRange();
    verify(silPeerWithSupport, times(1)).send(any(BlockRangeUpdateMessage.class));
  }

  @Test
  public void shouldSendBlockRangeOnlyToSil69Peers() throws PeerConnection.PeerNotConnected {
    setupPeers(silPeerWithoutSupport, silPeerWithSupport);
    when(silPeerWithSupport.hasSupportForMessage(SilProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(true);
    when(silPeerWithoutSupport.hasSupportForMessage(SilProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(false);
    broadcastBlockRange();
    verify(silPeerWithoutSupport, never()).send(any(BlockRangeUpdateMessage.class));
    verify(silPeerWithSupport, times(1)).send(any(BlockRangeUpdateMessage.class));
  }

  private void setupPeers(final SilPeer... peers) {
    when(silContext.getSilPeers()).thenReturn(silPeers);
    when(silPeers.streamAvailablePeers())
        .thenReturn(Stream.of(peers).map(SilPeerImmutableAttributes::from));
    for (SilPeer silPeer : peers) {
      ChainState chainState = Mockito.mock(ChainState.class);

      Mockito.when(silPeer.chainState()).thenReturn(chainState);
      Mockito.when(chainState.getEstimatedHeight()).thenReturn(0L);
      Mockito.when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));
      Mockito.when(silPeer.getReputation()).thenReturn(new PeerReputation());
      PeerConnection connection = mock(PeerConnection.class);
      Mockito.when(silPeer.getConnection()).thenReturn(connection);
    }
  }

  private void broadcastBlockRange() {
    long startBlockNumber = 0L;
    long endBlockNumber = 1L;
    Hash endBlockHash = Hash.ZERO;
    blockRangeBroadcaster.broadcastBlockRange(startBlockNumber, endBlockNumber, endBlockHash);
  }

  @Test
  public void shouldSendCorrectBlockRangeToPeers() {
    final long expectedEarliestBlock = 10L;
    final long expectedLatestBlockNumber = 20L;
    final Hash expectedBlockHash = Hash.wrap(Bytes32.fromHexString("0x0B"));

    setupPeers(silPeerWithoutSupport, silPeerWithSupport);
    setupBlockchain(expectedEarliestBlock, expectedLatestBlockNumber, expectedBlockHash);

    blockRangeBroadcaster.broadcastBlockRange();
    verify(blockRangeBroadcaster, times(1))
        .broadcastBlockRange(expectedEarliestBlock, expectedLatestBlockNumber, expectedBlockHash);
  }

  private void setupBlockchain(
      final long earliestBlockNumber, final long latestBlockNumber, final Hash expectedBlockHash) {
    final BlockHeader latestBlockHeader = mock(BlockHeader.class);
    when(latestBlockHeader.getNumber()).thenReturn(latestBlockNumber);
    when(latestBlockHeader.getHash()).thenReturn(expectedBlockHash);
    when(blockchain.getEarliestBlockNumber()).thenReturn(Optional.of(earliestBlockNumber));
    when(blockchain.getChainHeadHeader()).thenReturn(latestBlockHeader);
  }

  @Test
  public void shouldNotDisconnectIfLatestBlockNumberIsGreaterThanEarliest() {
    final SilPeer peer = mock(SilPeer.class);
    handleBlockRangeUpdateMessage(peer, 0L, 1L);
    verify(peer, never()).disconnect(any());
  }

  @Test
  public void shouldNotDisconnectIfLatestBlockNumberIsEqualToEarliest() {
    final SilPeer peer = mock(SilPeer.class);
    handleBlockRangeUpdateMessage(peer, 0L, 0L);
    verify(peer, never()).disconnect(any());
  }

  @Test
  public void shouldDisconnectIfLatestBlockNumberIsLessThanEarliest() {
    final SilPeer peer = mock(SilPeer.class);
    handleBlockRangeUpdateMessage(peer, 1L, 0L);
    verify(peer)
        .disconnect(DisconnectMessage.DisconnectReason.SUBPROTOCOL_TRIGGERED_INVALID_BLOCK_RANGE);
  }

  private void handleBlockRangeUpdateMessage(
      final SilPeer peer, final long earliestBlockNumber, final long latestBlockNumber) {
    BlockRangeUpdateMessage message =
        BlockRangeUpdateMessage.create(earliestBlockNumber, latestBlockNumber, Hash.ZERO);
    SilMessage silMessage = new SilMessage(peer, message);
    blockRangeBroadcaster.handleBlockRangeUpdateMessage(silMessage);
  }
}
