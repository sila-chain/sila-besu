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
package org.hyperledger.besu.sila.api.jsonrpc.internal.methods;

import org.hyperledger.besu.sila.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.PeerResult;
import org.hyperledger.besu.sila.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.sila.sil.manager.SilPeerImmutableAttributes;
import org.hyperledger.besu.sila.sil.manager.SilPeers;

import java.util.stream.Collectors;

public class AdminPeers implements JsonRpcMethod {
  private final SilPeers silPeers;

  public AdminPeers(final SilPeers silPeers) {
    this.silPeers = silPeers;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_PEERS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    try {
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          silPeers
              .streamAllPeers()
              .map(SilPeerImmutableAttributes::silPeer)
              .map(PeerResult::fromEthPeer)
              .collect(Collectors.toList()));
    } catch (P2PDisabledException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.P2P_DISABLED);
    }
  }
}
