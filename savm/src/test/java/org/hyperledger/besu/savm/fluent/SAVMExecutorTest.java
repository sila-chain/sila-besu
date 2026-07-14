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
package org.hyperledger.besu.savm.fluent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.frame.MessageFrame;
import org.hyperledger.besu.savm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.operation.OperationRegistry;
import org.hyperledger.besu.savm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.savm.processor.ContractCreationProcessor;
import org.hyperledger.besu.savm.processor.MessageCallProcessor;
import org.hyperledger.besu.savm.tracing.OpCodeTracerConfigBuilder;
import org.hyperledger.besu.savm.tracing.OpCodeTracerConfigBuilder.OpCodeTracerConfig;
import org.hyperledger.besu.savm.tracing.StreamingOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.MultimapBuilder;
import jakarta.validation.constraints.NotNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class SAVMExecutorTest {

  @Test
  void customSAVM() {
    var subject =
        new SAVMExecutor(
            SavmSpec.savmSpec(
                new SAVM(
                    new OperationRegistry(),
                    new FrontierGasCalculator(),
                    SavmConfiguration.DEFAULT,
                    SavmSpecVersion.EXPERIMENTAL_SIPS)));
    assertThat(subject).isNotNull();
  }

  @Test
  void nullSavmSpec() {
    assertThrows(NullPointerException.class, () -> new SAVMExecutor((SavmSpec) null));
  }

  @Test
  void executeBytes() {
    var result =
        new SAVMExecutor(SavmSpec.savmSpec(SavmSpecVersion.SHANGHAI))
            .worldUpdater(createSimpleWorld().updater())
            .execute(Bytes.fromHexString("0x6001600255"), Bytes.EMPTY, Wei.ZERO, Address.ZERO);
    assertThat(result).isNotNull();
  }

  @Test
  void giantExecuteStack() {
    SimpleWorld simpleWorld = createSimpleWorld();

    var tracer =
        new StreamingOperationTracer(
            System.out,
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceMemory(false)
                .traceStack(true)
                .traceReturnData(true)
                .traceStorage(false)
                .build());
    var result =
        new SAVMExecutor(
                SavmSpec.savmSpec(SavmSpecVersion.SHANGHAI)
                    .precompileContractRegistry(new PrecompileContractRegistry())
                    .requireDeposit(false)
                    .initialNonce(42)
                    .contractValidationRules(List.of())
                    .forceCommitAddresses(List.of()))
            .messageFrameType(MessageFrame.Type.CONTRACT_CREATION)
            .worldUpdater(simpleWorld.updater())
            .tracer(tracer)
            .contract(Address.fromHexString("0x100"))
            .gas(15_000_000L)
            .sender(Address.fromHexString("0x200"))
            .receiver(Address.fromHexString("0x300"))
            .coinbase(Address.fromHexString("0x400"))
            .number(1)
            .timestamp(9999)
            .gasLimit(15_000_000)
            .commitWorldState()
            .gasPriceGWei(Wei.ONE)
            .blobGasPrice(Wei.ONE)
            .callData(Bytes.fromHexString("0x12345678"))
            .silValue(Wei.fromSil(1))
            .code(Bytes.fromHexString("0x6001600255"))
            .blockValues(new SimpleBlockValues())
            .difficulty(Bytes.ofUnsignedLong(1L))
            .mixHash(Bytes32.ZERO)
            .baseFee(Wei.ONE)
            .number(1)
            .timestamp(100L)
            .gasLimit(15_000_000L)
            .blockHashLookup((__, ___) -> Hash.ZERO)
            .versionedHashes(Optional.empty())
            .warmAddress(Address.ZERO)
            .accessListWarmStorage(
                Address.ZERO, Bytes32.ZERO, Bytes32.leftPad(Bytes.ofUnsignedLong(2L)))
            .messageCallProcessor(new MessageCallProcessor(null, null))
            .contractCallProcessor(new ContractCreationProcessor(null, true, null, 1L))
            .execute();
    assertThat(result).isNotNull();
  }

  @Test
  void anternateExecStack() {
    SimpleWorld simpleWorld = createSimpleWorld();
    var result =
        new SAVMExecutor(SavmSpec.savmSpec(SavmSpecVersion.SHANGHAI))
            .worldUpdater(simpleWorld.updater())
            .messageFrameType(MessageFrame.Type.MESSAGE_CALL)
            .code(Bytes.fromHexString("0x6001600255"))
            .prevRandao(Bytes32.ZERO)
            .accessListWarmAddresses(Set.of())
            .accessListWarmStorage(MultimapBuilder.linkedHashKeys().arrayListValues().build())
            .execute();
    assertThat(result).isNotNull();
  }

  @NotNull
  private static SimpleWorld createSimpleWorld() {
    SimpleWorld simpleWorld = new SimpleWorld();

    simpleWorld.createAccount(Address.fromHexString("0x0"), 1, Wei.fromSil(100));
    simpleWorld.createAccount(Address.fromHexString("0x100"), 1, Wei.fromSil(100));
    simpleWorld.createAccount(Address.fromHexString("0x200"), 1, Wei.fromSil(100));
    simpleWorld.createAccount(Address.fromHexString("0x300"), 1, Wei.fromSil(100));
    simpleWorld.createAccount(Address.fromHexString("0x400"), 1, Wei.fromSil(100));
    return simpleWorld;
  }
}
