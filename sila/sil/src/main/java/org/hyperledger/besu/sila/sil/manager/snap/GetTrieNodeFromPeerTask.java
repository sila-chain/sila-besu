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

import static java.util.Collections.emptyMap;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.manager.PeerRequest;
import org.hyperledger.besu.sila.sil.manager.PendingPeerRequest;
import org.hyperledger.besu.sila.sil.manager.RequestManager;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.task.AbstractPeerRequestTask;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV1;
import org.hyperledger.besu.sila.sil.messages.snap.TrieNodesMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;

public class GetTrieNodeFromPeerTask extends AbstractPeerRequestTask<Map<Bytes, Bytes>> {

  private static final Logger LOG = getLogger(GetTrieNodeFromPeerTask.class);

  private final List<List<Bytes>> paths;
  private final BlockHeader blockHeader;

  private GetTrieNodeFromPeerTask(
      final SilContext silContext,
      final List<List<Bytes>> paths,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(silContext, SnapProtocol.NAME, SnapV1.TRIE_NODES, metricsSystem);
    this.paths = paths;
    this.blockHeader = blockHeader;
  }

  public static GetTrieNodeFromPeerTask forTrieNodes(
      final SilContext silContext,
      final Map<Bytes, List<Bytes>> paths,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new GetTrieNodeFromPeerTask(
        silContext,
        paths.entrySet().stream()
            .map(entry -> Lists.asList(entry.getKey(), entry.getValue().toArray(new Bytes[0])))
            .collect(Collectors.toList()),
        blockHeader,
        metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        new PeerRequest() {
          @Override
          public RequestManager.ResponseStream sendRequest(final SilPeer peer)
              throws PeerConnection.PeerNotConnected {
            LOG.atTrace()
                .setMessage("Requesting {} trie nodes from peer {}")
                .addArgument(paths.size())
                .addArgument(peer)
                .log();
            if (!peer.isServingSnap()) {
              LOG.debug(
                  "SilPeer that is not serving snap called in {}, {}",
                  GetAccountRangeFromPeerTask.class,
                  peer);
              throw new RuntimeException(
                  "SilPeer that is not serving snap called in "
                      + GetAccountRangeFromPeerTask.class);
            }
            return peer.getSnapTrieNode(blockHeader.getStateRoot(), paths);
          }

          @Override
          public boolean isSilPeerSuitable(final SilPeerImmutableAttributes silPeer) {
            return silPeer.isServingSnap();
          }
        },
        blockHeader.getNumber());
  }

  @Override
  protected Optional<Map<Bytes, Bytes>> processResponse(
      final boolean streamClosed, final MessageData message, final SilPeer peer) {
    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.of(emptyMap());
    }
    final TrieNodesMessage trieNodes = TrieNodesMessage.readFrom(message);
    final ArrayDeque<Bytes> nodes = trieNodes.nodes(true);
    return mapNodeDataByPath(nodes);
  }

  private Optional<Map<Bytes, Bytes>> mapNodeDataByPath(final ArrayDeque<Bytes> nodeData) {
    final Map<Bytes, Bytes> nodeDataByPath = new HashMap<>();
    paths.forEach(
        list -> {
          int i = 1;
          if (!nodeData.isEmpty() && i == list.size()) {
            Bytes bytes = nodeData.removeFirst();
            nodeDataByPath.put(list.get(0), bytes);
          } else {
            while (!nodeData.isEmpty() && i < list.size()) {
              Bytes bytes = nodeData.removeFirst();
              nodeDataByPath.put(Bytes.concatenate(list.get(0), list.get(i)), bytes);
              i++;
            }
          }
        });
    return Optional.of(nodeDataByPath);
  }
}
