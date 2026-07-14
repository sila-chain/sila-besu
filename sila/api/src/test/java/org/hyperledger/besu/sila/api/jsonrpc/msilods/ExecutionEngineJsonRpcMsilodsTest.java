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
package org.hyperledger.besu.sila.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.sila.blockcreation.MiningCoordinator;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

class ExecutionEngineJsonRpcMethodsTest {
  /**
   * Ensures that all methods returned by create() are valid and declared "engine_" methods. This
   * protects against accidental omissions from the RpcMethod enum, which is used by
   * engine_exchangeCapabilities
   */
  @Test
  void testGetSupportedMethods() {
    MiningCoordinator miningCoordinator = mock(MergeMiningCoordinator.class);
    when(miningCoordinator.isCompatibleWithEngineApi()).thenReturn(true);
    ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
    when(protocolSchedule.milestoneFor(any())).thenReturn(Optional.of(0L));
    ExecutionEngineJsonRpcMethods methods =
        new ExecutionEngineJsonRpcMethods(
            miningCoordinator,
            protocolSchedule,
            mock(ProtocolContext.class),
            mock(SilPeers.class),
            mock(Vertx.class),
            "testClient",
            "testCommit",
            mock(TransactionPool.class),
            mock(MetricsSystem.class));

    Map<String, JsonRpcMethod> engineMethods = methods.create();
    List<String> expectedMethodNames =
        Arrays.stream(RpcMethod.values())
            .map(RpcMethod::getMethodName)
            .filter(name -> name.startsWith("engine_"))
            .toList();

    // All expected method names should be present in the result
    for (String expectedMethod : expectedMethodNames) {
      assertThat(engineMethods)
          .as("Method '%s' should be present in engine methods", expectedMethod)
          .containsKey(expectedMethod);
    }

    // All keys in the map starting with "engine_" must be valid method names
    for (String actualMethod : engineMethods.keySet()) {
      if (actualMethod.startsWith("engine_")) {
        assertThat(expectedMethodNames)
            .as("Unexpected engine method: '%s'", actualMethod)
            .contains(actualMethod);
      }
    }
  }
}
