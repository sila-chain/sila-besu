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
import static org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.GasLimitCalculator;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.sila.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.sila.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.sila-mainnet.SilaCancunTargetingGasLimitCalculator;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpec;
import org.hyperledger.besu.sila.sila-mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.sila.sila-mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.savm.gascalculator.SilaCancunGasCalculator;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaShanghaiGasCalculator;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SilBlobBaseFeeTest {
  private static final int MAX_BLOBS_PER_BLOCK = 6;
  private static final int TARGET_BLOBS_PER_BLOCK = 3;
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private MutableBlockchain blockchain;
  private SilBlobBaseFee method;
  private ProtocolSchedule protocolSchedule;

  @BeforeEach
  public void setUp() {
    protocolSchedule = mock(ProtocolSchedule.class);
    Block genesisBlock = blockDataGenerator.genesisBlock();
    blockchain = createInMemoryBlockchain(genesisBlock);
    blockDataGenerator
        .blockSequence(genesisBlock, 10)
        .forEach(block -> blockchain.appendBlock(block, blockDataGenerator.receipts(block)));
    method = new SilBlobBaseFee(blockchain, protocolSchedule);
  }

  /** Tests that the method returns the expected blob base fee */
  @Test
  public void shouldReturnBlobBaseFee() {
    final SilaCancunTargetingGasLimitCalculator gasLimitCalculator =
        new SilaCancunTargetingGasLimitCalculator(
            0L,
            FeeMarket.cancunDefault(0L, Optional.empty()),
            new SilaCancunGasCalculator(),
            MAX_BLOBS_PER_BLOCK,
            TARGET_BLOBS_PER_BLOCK);
    configureProtocolSpec(
        FeeMarket.cancunDefault(5, Optional.empty()),
        new SilaCancunGasCalculator(),
        gasLimitCalculator);
    assertThat(requestBlobBaseFee().getResult()).isEqualTo("0x1");
  }

  /** Tests that the method returns zero for forks that do not support blob transactions */
  @Test
  public void shouldReturnZeroForNonBlobForks() {
    final SilaCancunTargetingGasLimitCalculator gasLimitCalculator =
        new SilaCancunTargetingGasLimitCalculator(
            0L,
            FeeMarket.cancunDefault(0L, Optional.empty()),
            new SilaShanghaiGasCalculator(),
            MAX_BLOBS_PER_BLOCK,
            TARGET_BLOBS_PER_BLOCK);
    configureProtocolSpec(
        FeeMarket.london(5, Optional.empty()), new SilaShanghaiGasCalculator(), gasLimitCalculator);
    assertThat(requestBlobBaseFee().getResult()).isEqualTo("0x0");
  }

  private void configureProtocolSpec(
      final BaseFeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator) {
    ProtocolSpec spec = mock(ProtocolSpec.class);
    when(spec.getFeeMarket()).thenReturn(feeMarket);
    when(spec.getGasCalculator()).thenReturn(gasCalculator);
    when(spec.getGasLimitCalculator()).thenReturn(gasLimitCalculator);
    when(protocolSchedule.getForNextBlockHeader(
            blockchain.getChainHeadHeader(), blockchain.getChainHeadHeader().getTimestamp()))
        .thenReturn(spec);
  }

  private JsonRpcSuccessResponse requestBlobBaseFee() {
    return (JsonRpcSuccessResponse)
        method.response(
            new JsonRpcRequestContext(new JsonRpcRequest("2.0", "sil_blobBaseFee", null)));
  }
}
