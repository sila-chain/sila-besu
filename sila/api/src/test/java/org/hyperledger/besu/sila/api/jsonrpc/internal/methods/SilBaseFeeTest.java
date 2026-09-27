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
package org.hyperledger.besu.sila.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SilBaseFeeTest {

  private BlockchainQueries blockchainQueries;
  private SilBaseFee method;

  @BeforeEach
  public void setUp() {
    blockchainQueries = mock(BlockchainQueries.class);
    method = new SilBaseFee(blockchainQueries);
  }

  @Test
  public void shouldReturnBaseFee() {
    when(blockchainQueries.getNextBlockBaseFee()).thenReturn(Optional.of(Wei.of(7)));
    assertThat(requestBaseFee().getResult()).isEqualTo("0x7");
  }

  @Test
  public void shouldReturnNullForPreLondonFork() {
    when(blockchainQueries.getNextBlockBaseFee()).thenReturn(Optional.empty());
    assertThat(requestBaseFee().getResult()).isNull();
  }

  private JsonRpcSuccessResponse requestBaseFee() {
    return (JsonRpcSuccessResponse)
        method.response(new JsonRpcRequestContext(new JsonRpcRequest("2.0", "sil_baseFee", null)));
  }
}
