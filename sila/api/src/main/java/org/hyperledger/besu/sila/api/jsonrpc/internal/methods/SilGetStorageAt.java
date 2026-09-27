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
import org.hyperledger.besu.datatypes.parameters.UInt256Parameter;
import org.hyperledger.besu.sila.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.sila.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.sila.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.sila.api.query.BlockchainQueries;

import org.apache.tuweni.units.bigints.UInt256;

public class SilGetStorageAt extends AbstractBlockParameterOrBlockHashMethod {
  public SilGetStorageAt(final BlockchainQueries blockchainQueries) {
    super(blockchainQueries);
  }

  @Override
  public String getName() {
    return RpcMethod.SIL_GET_STORAGE_AT.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return blockParameterOrBlockHashWithLatestDefault(request, 2);
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
    final UInt256 position;
    try {
      position = request.getRequiredParameter(1, UInt256Parameter.class).getValue();
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid position parameter (index 1)", RpcErrorType.INVALID_POSITION_PARAMS, e);
    }
    return blockchainQueries
        .get()
        .storageAt(address, position, blockHash)
        .map(UInt256::toHexString)
        .orElse(null);
  }
}
