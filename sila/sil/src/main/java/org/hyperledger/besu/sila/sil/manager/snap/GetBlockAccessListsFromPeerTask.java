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

import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.SyncBlockAccessList;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.manager.PeerRequest;
import org.hyperledger.besu.sila.sil.manager.PendingPeerRequest;
import org.hyperledger.besu.sila.sil.manager.RequestManager;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.exceptions.ProtocolViolationException;
import org.hyperledger.besu.sila.sil.manager.task.AbstractPeerRequestTask;
import org.hyperledger.besu.sila.sil.messages.snap.BlockAccessListsMessage;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV2;
import org.hyperledger.besu.sila.silaMainnet.BodyValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;

public class GetBlockAccessListsFromPeerTask
    extends AbstractPeerRequestTask<List<SyncBlockAccessList>> {

  private static final Logger LOG = getLogger(GetBlockAccessListsFromPeerTask.class);

  private final List<BlockHeader> blockHeaders;

  public GetBlockAccessListsFromPeerTask(
      final SilContext silContext,
      final List<BlockHeader> blockHeaders,
      final MetricsSystem metricsSystem) {
    super(silContext, SnapProtocol.NAME, SnapV2.BLOCK_ACCESS_LISTS, metricsSystem);
    this.blockHeaders = blockHeaders;
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        new PeerRequest() {
          @Override
          public RequestManager.ResponseStream sendRequest(final SilPeer peer)
              throws PeerConnection.PeerNotConnected {
            LOG.atTrace()
                .setMessage("Requesting {} block access lists from {} .")
                .addArgument(blockHeaders::size)
                .addArgument(peer)
                .log();
            if (!peer.isServingSnap()) {
              LOG.atDebug()
                  .setMessage("SilPeer that is not serving snap called in {}, peer: {}")
                  .addArgument(GetBlockAccessListsFromPeerTask.class)
                  .addArgument(peer)
                  .log();
              throw new RuntimeException(
                  "SilPeer that is not serving snap called in "
                      + GetBlockAccessListsFromPeerTask.class);
            }
            if (!peer.getAgreedCapabilities().contains(SnapProtocol.SNAP2)) {
              LOG.atDebug()
                  .setMessage("SilPeer does not support snap/2 in {}, peer: {}")
                  .addArgument(GetBlockAccessListsFromPeerTask.class)
                  .addArgument(peer)
                  .log();
              throw new RuntimeException(
                  "SilPeer does not support snap/2 in " + GetBlockAccessListsFromPeerTask.class);
            }
            return peer.getSnapBlockAccessLists(
                blockHeaders.stream().map(BlockHeader::getBlockHash).toList());
          }

          @Override
          public boolean isSilPeerSuitable(final SilPeerImmutableAttributes silPeer) {
            return silPeer.isServingSnap()
                && silPeer.silPeer().getAgreedCapabilities().contains(SnapProtocol.SNAP2);
          }
        },
        blockHeaders.stream()
            .mapToLong(BlockHeader::getNumber)
            .max()
            .orElse(BlockHeader.GENESIS_BLOCK_NUMBER));
  }

  @Override
  protected Optional<List<SyncBlockAccessList>> processResponse(
      final boolean streamClosed, final MessageData message, final SilPeer peer) {
    if (streamClosed) {
      return Optional.empty();
    }

    final List<SyncBlockAccessList> result = new ArrayList<>();
    int index = 0;
    for (final Bytes balRlp : BlockAccessListsMessage.readFrom(message).blockAccessListsRaw(true)) {
      if (index >= blockHeaders.size()) {
        throw new ProtocolViolationException(
            "Received more block access lists than requested: expected %d but got at least %d"
                .formatted(blockHeaders.size(), index + 1));
      }
      final SyncBlockAccessList syncBlockAccessList = new SyncBlockAccessList(balRlp);
      // TODO: Penalize peers maliciously denying BALs
      if (!syncBlockAccessList.isUnavailable()) {
        final int currentIndex = index;
        final BlockHeader blockHeader = blockHeaders.get(currentIndex);
        final Hash expected =
            blockHeader
                .getBalHash()
                .orElseThrow(
                    () ->
                        new ProtocolViolationException(
                            "Missing expected block access list hash at index %d for block %s"
                                .formatted(currentIndex, blockHeader.getHash())));
        final Hash actual = BodyValidation.balHash(syncBlockAccessList);
        if (!actual.equals(expected)) {
          throw new ProtocolViolationException(
              "Received block access list with invalid hash at index %d: expected %s, got %s"
                  .formatted(index, expected, actual));
        }
      }
      result.add(syncBlockAccessList);
      index++;
    }
    return Optional.of(result);
  }
}
