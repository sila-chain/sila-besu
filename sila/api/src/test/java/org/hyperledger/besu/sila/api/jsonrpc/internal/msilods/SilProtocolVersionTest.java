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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class SilProtocolVersionTest {
  private SilProtocolVersion method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String SIL_METHOD = "sil_protocolVersion";
  private Set<Capability> supportedCapabilities;

  @Test
  public void returnsCorrectMethodName() {
    setupSupportedSilProtocols(SilProtocol.LATEST);
    assertThat(method.getName()).isEqualTo(SIL_METHOD);
  }

  @Test
  public void shouldReturn68WhenMaxProtocolIsSIL68() {
    Capability capability = SilProtocol.SIL68;
    setupSupportedSilProtocols(capability);
    String expectedVersion = "0x" + Integer.toHexString(capability.getVersion());
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedVersion);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnNullNoSilProtocolsSupported() {
    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new SilProtocolVersion(supportedCapabilities);

    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), null);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturn68WhenMixedProtocolsSupported() {
    Capability capability = SilProtocol.SIL68;
    setupSupportedSilProtocols(capability);
    String expectedVersion = "0x" + Integer.toHexString(capability.getVersion());
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new SilProtocolVersion(supportedCapabilities);

    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedVersion);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, SIL_METHOD, params));
  }

  private void setupSupportedSilProtocols(final Capability capability) {
    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(capability);
    method = new SilProtocolVersion(supportedCapabilities);
  }
}
