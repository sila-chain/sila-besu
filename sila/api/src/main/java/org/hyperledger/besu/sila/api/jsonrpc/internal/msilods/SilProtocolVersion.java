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
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;

import java.util.OptionalInt;
import java.util.Set;

public class SilProtocolVersion implements JsonRpcMethod {

  private final Integer highestSilVersion;

  public SilProtocolVersion(final Set<Capability> supportedCapabilities) {
    final OptionalInt version =
        supportedCapabilities.stream()
            .filter(cap -> SilProtocol.NAME.equals(cap.getName()))
            .mapToInt(Capability::getVersion)
            .max();
    highestSilVersion = version.isPresent() ? version.getAsInt() : null;
  }

  @Override
  public String getName() {
    return RpcMethod.SIL_PROTOCOL_VERSION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        highestSilVersion == null ? null : Quantity.create(highestSilVersion));
  }
}
