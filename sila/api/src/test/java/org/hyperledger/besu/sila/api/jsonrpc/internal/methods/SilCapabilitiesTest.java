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
package org.hyperledger.besu.sila.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;

import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SilCapabilitiesTest {

  private static final String SIL_METHOD = "sil_capabilities";
  private static final String JSON_RPC_VERSION = "2.0";

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private Blockchain blockchain;
  @Mock private BlockHeader chainHeadHeader;
  @Mock private BlockHeader genesisBlockHeader;
  @Mock private WorldStateArchive worldStateArchive;

  private SilCapabilities method;

  @BeforeEach
  public void setUp() {
    method = new SilCapabilities(blockchainQueries);
  }

  private void stubChainHead() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadHeader);
    when(chainHeadHeader.getNumber()).thenReturn(42L);
    when(chainHeadHeader.getHash())
        .thenReturn(
            Hash.fromHexString(
                "0x1111111111111111111111111111111111111111111111111111111111111111"));
    when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(blockchain.getGenesisBlockHeader()).thenReturn(genesisBlockHeader);
    when(genesisBlockHeader.getStateRoot()).thenReturn(Hash.ZERO);
    when(genesisBlockHeader.getHash()).thenReturn(Hash.ZERO);
    when(worldStateArchive.isWorldStateAvailable(Hash.ZERO, Hash.ZERO)).thenReturn(true);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(SIL_METHOD);
  }

  @Test
  public void shouldIncludeHeadAndOldestForBlockBackedResourcesWhenAvailable() {
    stubChainHead();
    when(blockchain.getEarliestBlockNumber()).thenReturn(Optional.of(10L));

    final JsonRpcSuccessResponse response = successResponse(requestWithParams());
    final ObjectNode result = (ObjectNode) response.getResult();

    assertThat(result.get("head").get("number").asText()).isEqualTo("0x2a");
    assertThat(result.get("head").get("hash").asText())
        .isEqualTo("0x1111111111111111111111111111111111111111111111111111111111111111");

    assertThat(result.get("state").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("state").get("oldestBlock").asText()).isEqualTo("0xa");

    assertThat(result.get("tx").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("tx").get("oldestBlock").asText()).isEqualTo("0xa");

    assertThat(result.get("logs").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("logs").get("oldestBlock").asText()).isEqualTo("0xa");

    assertThat(result.get("receipts").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("receipts").get("oldestBlock").asText()).isEqualTo("0xa");

    assertThat(result.get("blocks").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("blocks").get("oldestBlock").asText()).isEqualTo("0xa");

    assertThat(result.get("stateproofs").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("stateproofs").get("oldestBlock").asText()).isEqualTo("0xa");
  }

  @Test
  public void shouldOmitOldestForBlockBackedResourcesWhenNotAvailable() {
    stubChainHead();
    when(blockchain.getEarliestBlockNumber()).thenReturn(Optional.empty());

    final JsonRpcSuccessResponse response = successResponse(requestWithParams());
    final ObjectNode result = (ObjectNode) response.getResult();

    assertThat(result.get("tx").get("oldestBlock")).isNull();
    assertThat(result.get("logs").get("oldestBlock")).isNull();
    assertThat(result.get("receipts").get("oldestBlock")).isNull();
    assertThat(result.get("blocks").get("oldestBlock")).isNull();
  }

  @Test
  public void shouldReportStateAndStateproofsDisabledWhenGenesisStateUnavailable() {
    stubChainHead();
    when(worldStateArchive.isWorldStateAvailable(Hash.ZERO, Hash.ZERO)).thenReturn(false);
    when(blockchain.getEarliestBlockNumber()).thenReturn(Optional.of(1000L));

    final JsonRpcSuccessResponse response = successResponse(requestWithParams());
    final ObjectNode result = (ObjectNode) response.getResult();

    assertThat(result.get("state").get("disabled").asBoolean()).isTrue();
    assertThat(result.get("stateproofs").get("disabled").asBoolean()).isTrue();
    assertThat(result.get("tx").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("logs").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("receipts").get("disabled").asBoolean()).isFalse();
    assertThat(result.get("blocks").get("disabled").asBoolean()).isFalse();

    assertThat(result.get("tx").get("oldestBlock").asText()).isEqualTo("0x3e8");
    assertThat(result.get("logs").get("oldestBlock").asText()).isEqualTo("0x3e8");
    assertThat(result.get("receipts").get("oldestBlock").asText()).isEqualTo("0x3e8");
    assertThat(result.get("blocks").get("oldestBlock").asText()).isEqualTo("0x3e8");
    assertThat(result.get("state").get("oldestBlock")).isNull();
    assertThat(result.get("stateproofs").get("oldestBlock")).isNull();
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, SIL_METHOD, params));
  }

  private JsonRpcSuccessResponse successResponse(final JsonRpcRequestContext requestContext) {
    final JsonRpcResponse response = method.response(requestContext);
    return (JsonRpcSuccessResponse) response;
  }
}
