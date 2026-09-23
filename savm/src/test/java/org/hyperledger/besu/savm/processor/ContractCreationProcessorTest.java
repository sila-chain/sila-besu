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
package org.hyperledger.besu.savm.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.savm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.savm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.SilaMainnetSAVMs;
import org.hyperledger.besu.savm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.savm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.savm.frame.MessageFrame;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.savm.tracing.OperationTracer;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Nested
@ExtendWith(MockitoExtension.class)
class ContractCreationProcessorTest
    extends AbstractMessageProcessorTest<ContractCreationProcessor> {

  SAVM savm = SilaMainnetSAVMs.futureSips(SavmConfiguration.DEFAULT);

  private ContractCreationProcessor processor;

  @Test
  void shouldThrowAnExceptionWhenCodeContractFormatInvalidPreEOF() {
    processor =
        new ContractCreationProcessor(
            savm, true, Collections.singletonList(PrefixCodeRule.of()), 1);
    final Bytes contractCode = Bytes.fromHexString("EF01010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    // SIP-8037: validation failures use EXCEPTIONAL_HALT so handleStateGasHalt refunds
    // execution state gas to the reservoir; gasRemaining is cleared by exceptionalHalt() in
    // process(), not codeSuccess() in isolation.
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeContractIsValid() {
    processor =
        new ContractCreationProcessor(
            savm, true, Collections.singletonList(PrefixCodeRule.of()), 1);
    final Bytes contractCode = Bytes.fromHexString("0101010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldNotThrowAnExceptionWhenPrefixCodeRuleNotAdded() {
    processor = new ContractCreationProcessor(savm, true, Collections.emptyList(), 1);
    final Bytes contractCode = Bytes.fromHexString("0F01010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            savm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(SavmSpecVersion.SPURIOUS_DRAGON, SavmConfiguration.DEFAULT)),
            1);
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(SavmSpecVersion.SPURIOUS_DRAGON.getMaxCodeSize() + 1));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    // SIP-8037: validation failures use EXCEPTIONAL_HALT so handleStateGasHalt refunds
    // execution state gas to the reservoir; gasRemaining is cleared by exceptionalHalt() in
    // process(), not codeSuccess() in isolation.
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            savm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(SavmSpecVersion.SPURIOUS_DRAGON, SavmConfiguration.DEFAULT)),
            1);
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(SavmSpecVersion.SPURIOUS_DRAGON.getMaxCodeSize()));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(5_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldRejectDeployedCodeAboveSilaAmsterdamLimit() {
    processor =
        new ContractCreationProcessor(
            savm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(SavmSpecVersion.AMSTERDAM, SavmConfiguration.DEFAULT)),
            1);
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(SavmSpecVersion.AMSTERDAM.getMaxCodeSize() + 1));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    // SIP-8037: validation failures use EXCEPTIONAL_HALT so handleStateGasHalt refunds
    // execution state gas to the reservoir; gasRemaining is cleared by exceptionalHalt() in
    // process(), not codeSuccess() in isolation.
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void shouldAcceptDeployedCodeAtSilaAmsterdamLimit() {
    processor =
        new ContractCreationProcessor(
            savm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(SavmSpecVersion.AMSTERDAM, SavmConfiguration.DEFAULT)),
            1);
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(SavmSpecVersion.AMSTERDAM.getMaxCodeSize()));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    // SIP-7954: 64KiB code deposit costs 200 * 0x10000 = 13_107_200 regular gas.
    messageFrame.setGasRemaining(15_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldAcceptDeployedCodeBetweenOldAndNewSilaAmsterdamLimit() {
    processor =
        new ContractCreationProcessor(
            savm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(SavmSpecVersion.AMSTERDAM, SavmConfiguration.DEFAULT)),
            1);
    final Bytes contractCode = Bytes.fromHexString("00".repeat(0x6001));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeSizeRuleNotAdded() {
    processor = new ContractCreationProcessor(savm, true, Collections.emptyList(), 1);
    final Bytes contractCode = Bytes.fromHexString("00".repeat(24 * 1024 + 1));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(5_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Override
  protected ContractCreationProcessor getAbstractMessageProcessor() {
    return new ContractCreationProcessor(savm, true, Collections.emptyList(), 1);
  }
}
