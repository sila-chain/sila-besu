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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.sila.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.sila.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;

import java.util.function.Supplier;

public class SilGetBalance extends AbstractBlockParameterOrBlockHashMethod {
  public SilGetBalance(final BlockchainQueries blockchainQueries) {
    super(blockchainQueries);
  }

  public SilGetBalance(final Supplier<BlockchainQueries> blockchainQueries) {
    super(blockchainQueries);
  }

  @Override
  public String getName() {
    return RpcMethod.SIL_GET_BALANCE.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return blockParameterOrBlockHashWithLatestDefault(request, 1);
  }

  @Override
  protected String resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    final Address address;
    try {
      address = request.getRequiredParameter(0, Address.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid address parameter (index 0)", RpcErrorType.INVALID_ADDRESS_PARAMS, e);
    }
    return blockchainQueries
        .get()
        .accountBalance(address, blockHash)
        .map(Quantity::create)
        .orElse(null);
  }
}
