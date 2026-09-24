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
package org.hyperledger.besu.savm;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.savm.operation.PushOperation.PUSH_BASE;
import static org.hyperledger.besu.savm.operation.SwapOperation.SWAP_BASE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.savm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.savm.frame.MessageFrame;
import org.hyperledger.besu.savm.frame.MessageFrame.State;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.internal.JumpDestOnlyCodeCache;
import org.hyperledger.besu.savm.internal.OverflowException;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.internal.UnderflowException;
import org.hyperledger.besu.savm.operation.AddModOperation;
import org.hyperledger.besu.savm.operation.AddModOperationOptimized;
import org.hyperledger.besu.savm.operation.AddOperation;
import org.hyperledger.besu.savm.operation.AddOperationOptimized;
import org.hyperledger.besu.savm.operation.AndOperation;
import org.hyperledger.besu.savm.operation.AndOperationOptimized;
import org.hyperledger.besu.savm.operation.ByteOperation;
import org.hyperledger.besu.savm.operation.ChainIdOperation;
import org.hyperledger.besu.savm.operation.CountLeadingZerosOperation;
import org.hyperledger.besu.savm.operation.DivOperation;
import org.hyperledger.besu.savm.operation.DivOperationOptimized;
import org.hyperledger.besu.savm.operation.DupNOperation;
import org.hyperledger.besu.savm.operation.DupOperation;
import org.hyperledger.besu.savm.operation.ExchangeOperation;
import org.hyperledger.besu.savm.operation.ExpOperation;
import org.hyperledger.besu.savm.operation.GtOperation;
import org.hyperledger.besu.savm.operation.InvalidOperation;
import org.hyperledger.besu.savm.operation.IsZeroOperation;
import org.hyperledger.besu.savm.operation.JumpDestOperation;
import org.hyperledger.besu.savm.operation.JumpOperation;
import org.hyperledger.besu.savm.operation.JumpiOperation;
import org.hyperledger.besu.savm.operation.LtOperation;
import org.hyperledger.besu.savm.operation.ModOperation;
import org.hyperledger.besu.savm.operation.ModOperationOptimized;
import org.hyperledger.besu.savm.operation.MulModOperation;
import org.hyperledger.besu.savm.operation.MulModOperationOptimized;
import org.hyperledger.besu.savm.operation.MulOperation;
import org.hyperledger.besu.savm.operation.MulOperationOptimized;
import org.hyperledger.besu.savm.operation.NotOperation;
import org.hyperledger.besu.savm.operation.NotOperationOptimized;
import org.hyperledger.besu.savm.operation.Operation;
import org.hyperledger.besu.savm.operation.Operation.OperationResult;
import org.hyperledger.besu.savm.operation.OperationRegistry;
import org.hyperledger.besu.savm.operation.OrOperation;
import org.hyperledger.besu.savm.operation.OrOperationOptimized;
import org.hyperledger.besu.savm.operation.PopOperation;
import org.hyperledger.besu.savm.operation.Push0Operation;
import org.hyperledger.besu.savm.operation.PushOperation;
import org.hyperledger.besu.savm.operation.SDivOperation;
import org.hyperledger.besu.savm.operation.SDivOperationOptimized;
import org.hyperledger.besu.savm.operation.SGtOperation;
import org.hyperledger.besu.savm.operation.SLtOperation;
import org.hyperledger.besu.savm.operation.SModOperation;
import org.hyperledger.besu.savm.operation.SModOperationOptimized;
import org.hyperledger.besu.savm.operation.SarOperation;
import org.hyperledger.besu.savm.operation.SarOperationOptimized;
import org.hyperledger.besu.savm.operation.ShlOperation;
import org.hyperledger.besu.savm.operation.ShlOperationOptimized;
import org.hyperledger.besu.savm.operation.ShrOperation;
import org.hyperledger.besu.savm.operation.ShrOperationOptimized;
import org.hyperledger.besu.savm.operation.SignExtendOperation;
import org.hyperledger.besu.savm.operation.StopOperation;
import org.hyperledger.besu.savm.operation.SubOperation;
import org.hyperledger.besu.savm.operation.SubOperationOptimized;
import org.hyperledger.besu.savm.operation.SwapNOperation;
import org.hyperledger.besu.savm.operation.SwapOperation;
import org.hyperledger.besu.savm.operation.VirtualOperation;
import org.hyperledger.besu.savm.operation.XorOperation;
import org.hyperledger.besu.savm.operation.XorOperationOptimized;
import org.hyperledger.besu.savm.tracing.OperationTracer;
import org.hyperledger.besu.savm.v2.operation.AddModOperationV2;
import org.hyperledger.besu.savm.v2.operation.AddOperationV2;
import org.hyperledger.besu.savm.v2.operation.DivOperationV2;
import org.hyperledger.besu.savm.v2.operation.ModOperationV2;
import org.hyperledger.besu.savm.v2.operation.MulModOperationV2;
import org.hyperledger.besu.savm.v2.operation.MulOperationV2;
import org.hyperledger.besu.savm.v2.operation.SDivOperationV2;
import org.hyperledger.besu.savm.v2.operation.SModOperationV2;
import org.hyperledger.besu.savm.v2.operation.SarOperationV2;
import org.hyperledger.besu.savm.v2.operation.ShlOperationV2;
import org.hyperledger.besu.savm.v2.operation.ShrOperationV2;
import org.hyperledger.besu.savm.v2.operation.SubOperationV2;

import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Savm. */
public class SAVM {
  private static final Logger LOG = LoggerFactory.getLogger(SAVM.class);

  /** The constant OVERFLOW_RESPONSE. */
  protected static final OperationResult OVERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);

  /** The constant UNDERFLOW_RESPONSE. */
  protected static final OperationResult UNDERFLOW_RESPONSE =
      new OperationResult(0L, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

  private final OperationRegistry operations;
  private final GasCalculator gasCalculator;
  private final Operation endOfScriptStop;
  private final SavmConfiguration savmConfiguration;
  private final SavmSpecVersion savmSpecVersion;

  // Optimized operation flags
  private final boolean enableConstantinople;
  private final boolean enableShanghai;
  private final boolean enableAmsterdam;
  private final boolean enableOsaka;

  private final JumpDestOnlyCodeCache jumpDestOnlyCodeCache;

  /**
   * Instantiates a new Savm.
   *
   * @param operations the operations
   * @param gasCalculator the gas calculator
   * @param savmConfiguration the savm configuration
   * @param savmSpecVersion the savm spec version
   */
  public SAVM(
      final OperationRegistry operations,
      final GasCalculator gasCalculator,
      final SavmConfiguration savmConfiguration,
      final SavmSpecVersion savmSpecVersion) {
    this.operations = operations;
    this.gasCalculator = gasCalculator;
    this.endOfScriptStop = new VirtualOperation(new StopOperation(gasCalculator));
    this.savmConfiguration = savmConfiguration;
    this.savmSpecVersion = savmSpecVersion;
    this.jumpDestOnlyCodeCache = new JumpDestOnlyCodeCache(savmConfiguration);

    enableConstantinople = SavmSpecVersion.CONSTANTINOPLE.ordinal() <= savmSpecVersion.ordinal();
    enableShanghai = SavmSpecVersion.SHANGHAI.ordinal() <= savmSpecVersion.ordinal();
    enableAmsterdam = SavmSpecVersion.AMSTERDAM.ordinal() <= savmSpecVersion.ordinal();
    enableOsaka = SavmSpecVersion.OSAKA.ordinal() <= savmSpecVersion.ordinal();
  }

  /**
   * Gets gas calculator.
   *
   * @return the gas calculator
   */
  public GasCalculator getGasCalculator() {
    return gasCalculator;
  }

  /**
   * Gets the max code size, taking configuration and version into account
   *
   * @return The max code size override, if not set the max code size for the SAVM version.
   */
  public int getMaxCodeSize() {
    return savmConfiguration.maxCodeSizeOverride().orElse(savmSpecVersion.maxCodeSize);
  }

  /**
   * Gets the max initcode Size, taking configuration and version into account
   *
   * @return The max initcode size override, if not set the max initcode size for the SAVM version.
   */
  public int getMaxInitcodeSize() {
    return savmConfiguration.maxInitcodeSizeOverride().orElse(savmSpecVersion.maxInitcodeSize);
  }

  /**
   * Returns the non-fork related configuration parameters of the SAVM.
   *
   * @return the SAVM configuration.
   */
  public SavmConfiguration getEvmConfiguration() {
    return savmConfiguration;
  }

  /**
   * Returns the configured SAVM spec version for this SAVM
   *
   * @return the savm spec version
   */
  public SavmSpecVersion getEvmVersion() {
    return savmSpecVersion;
  }

  /**
   * Return the ChainId this Executor is using, or empty if the SAVM version does not expose chain
   * ID.
   *
   * @return the ChainId, or empty if not exposed.
   */
  public Optional<Bytes> getChainId() {
    Operation op = operations.get(ChainIdOperation.OPCODE);
    if (op instanceof ChainIdOperation chainIdOperation) {
      return Optional.of(chainIdOperation.getChainId());
    } else {
      return Optional.empty();
    }
  }

  /**
   * Run to halt.
   *
   * @param frame the frame
   * @param operationTracer the tracing
   */
  // Note to maintainers: lots of Java idioms and OO principals are being set aside in the
  // name of performance. This is one of the hottest sections of code.
  //
  // Please benchmark before refactoring.
  public void runToHalt(final MessageFrame frame, @NonNull final OperationTracer operationTracer) {
    // do not remove assert! A single, monomorphic tracer, is allowed in the SAVM execution if
    // tracing is disabled for
    // optimization purposes
    assert operationTracer.isEnabled() || operationTracer == OperationTracer.NO_TRACING;

    if (savmConfiguration.enableEvmV2()) {
      runToHaltV2(frame, operationTracer);
      return;
    }
    savmSpecVersion.maybeWarnVersion();

    byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    Operation[] operationArray = operations.getOperations();
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      Operation currentOperation;
      int opcode;
      int pc = frame.getPC();
      if (pc < code.length) {
        opcode = code[pc] & 0xff;
        currentOperation = operationArray[opcode];
      } else {
        opcode = 0;
        currentOperation = endOfScriptStop;
      }
      frame.setCurrentOperation(currentOperation);
      operationTracer.tracePreExecution(frame);

      OperationResult result;
      try {
        result =
            switch (opcode) {
              case 0x00 -> StopOperation.staticOperation(frame);
              case 0x01 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? AddOperationOptimized.staticOperation(frame)
                      : AddOperation.staticOperation(frame);
              case 0x02 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? MulOperationOptimized.staticOperation(frame)
                      : MulOperation.staticOperation(frame);
              case 0x03 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? SubOperationOptimized.staticOperation(frame)
                      : SubOperation.staticOperation(frame);
              case 0x04 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? DivOperationOptimized.staticOperation(frame)
                      : DivOperation.staticOperation(frame);
              case 0x05 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? SDivOperationOptimized.staticOperation(frame)
                      : SDivOperation.staticOperation(frame);
              case 0x06 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? ModOperationOptimized.staticOperation(frame)
                      : ModOperation.staticOperation(frame);
              case 0x07 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? SModOperationOptimized.staticOperation(frame)
                      : SModOperation.staticOperation(frame);
              case 0x08 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? AddModOperationOptimized.staticOperation(frame)
                      : AddModOperation.staticOperation(frame);
              case 0x09 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? MulModOperationOptimized.staticOperation(frame)
                      : MulModOperation.staticOperation(frame);
              case 0x0a -> ExpOperation.staticOperation(frame, gasCalculator);
              case 0x0b -> SignExtendOperation.staticOperation(frame);
              case 0x0c, 0x0d, 0x0e, 0x0f -> InvalidOperation.invalidOperationResult(opcode);
              case 0x10 -> LtOperation.staticOperation(frame);
              case 0x11 -> GtOperation.staticOperation(frame);
              case 0x12 -> SLtOperation.staticOperation(frame);
              case 0x13 -> SGtOperation.staticOperation(frame);
              case 0x15 -> IsZeroOperation.staticOperation(frame);
              case 0x16 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? AndOperationOptimized.staticOperation(frame)
                      : AndOperation.staticOperation(frame);
              case 0x17 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? OrOperationOptimized.staticOperation(frame)
                      : OrOperation.staticOperation(frame);
              case 0x18 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? XorOperationOptimized.staticOperation(frame)
                      : XorOperation.staticOperation(frame);
              case 0x19 ->
                  savmConfiguration.enableOptimizedOpcodes()
                      ? NotOperationOptimized.staticOperation(frame)
                      : NotOperation.staticOperation(frame);
              case 0x1a -> ByteOperation.staticOperation(frame);
              case 0x1b ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          ShlOperation::staticOperation,
                          ShlOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1c ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          ShrOperation::staticOperation,
                          ShrOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1d ->
                  enableConstantinople
                      ? shiftOperation(
                          frame,
                          SarOperation::staticOperation,
                          SarOperationOptimized::staticOperation)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1e ->
                  enableOsaka
                      ? CountLeadingZerosOperation.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x50 -> PopOperation.staticOperation(frame);
              case 0x56 -> JumpOperation.staticOperation(frame);
              case 0x57 -> JumpiOperation.staticOperation(frame);
              case 0x5b -> JumpDestOperation.JUMPDEST_SUCCESS;
              case 0x5f ->
                  enableShanghai
                      ? Push0Operation.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x60, // PUSH1-32
                  0x61,
                  0x62,
                  0x63,
                  0x64,
                  0x65,
                  0x66,
                  0x67,
                  0x68,
                  0x69,
                  0x6a,
                  0x6b,
                  0x6c,
                  0x6d,
                  0x6e,
                  0x6f,
                  0x70,
                  0x71,
                  0x72,
                  0x73,
                  0x74,
                  0x75,
                  0x76,
                  0x77,
                  0x78,
                  0x79,
                  0x7a,
                  0x7b,
                  0x7c,
                  0x7d,
                  0x7e,
                  0x7f ->
                  PushOperation.staticOperation(frame, code, pc, opcode - PUSH_BASE);
              case 0x80, // DUP1-16
                  0x81,
                  0x82,
                  0x83,
                  0x84,
                  0x85,
                  0x86,
                  0x87,
                  0x88,
                  0x89,
                  0x8a,
                  0x8b,
                  0x8c,
                  0x8d,
                  0x8e,
                  0x8f ->
                  DupOperation.staticOperation(frame, opcode - DupOperation.DUP_BASE);
              case 0x90, // SWAP1-16
                  0x91,
                  0x92,
                  0x93,
                  0x94,
                  0x95,
                  0x96,
                  0x97,
                  0x98,
                  0x99,
                  0x9a,
                  0x9b,
                  0x9c,
                  0x9d,
                  0x9e,
                  0x9f ->
                  SwapOperation.staticOperation(frame, opcode - SWAP_BASE);
              case 0xe6 -> // DUPN (SIP-8024)
                  enableAmsterdam
                      ? DupNOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0xe7 -> // SWAPN (SIP-8024)
                  enableAmsterdam
                      ? SwapNOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0xe8 -> // EXCHANGE (SIP-8024)
                  enableAmsterdam
                      ? ExchangeOperation.staticOperation(frame, code, pc)
                      : InvalidOperation.invalidOperationResult(opcode);
              default -> { // unoptimized operations
                frame.setCurrentOperation(currentOperation);
                yield currentOperation.execute(frame, this);
              }
            };
      } catch (final OverflowException oe) {
        result = OVERFLOW_RESPONSE;
      } catch (final UnderflowException ue) {
        result = UNDERFLOW_RESPONSE;
      }
      final ExceptionalHaltReason haltReason = result.getHaltReason();
      if (haltReason != null) {
        LOG.trace("MessageFrame evaluation halted because of {}", haltReason);
        frame.setExceptionalHaltReason(Optional.of(haltReason));
        frame.setState(State.EXCEPTIONAL_HALT);
      } else if (frame.decrementRemainingGas(result.getGasCost()) < 0) {
        frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        frame.setState(State.EXCEPTIONAL_HALT);
      }
      if (frame.getState() == State.CODE_EXECUTING) {
        final int currentPC = frame.getPC();
        final int opSize = result.getPcIncrement();
        frame.setPC(currentPC + opSize);
      }
      operationTracer.tracePostExecution(frame, result);
    }
  }

  /**
   * SAVM v2 execution loop using long[] stack representation. Only opcodes explicitly listed in the
   * switch are handled via the v2 path; all others fall through to the v1 operation registry. This
   * skeleton stub establishes the dispatch structure for incremental v2 operation rollout.
   */
  // Note: like runToHalt, this is performance-critical code. Benchmark before refactoring.
  private void runToHaltV2(final MessageFrame frame, final OperationTracer operationTracer) {
    savmSpecVersion.maybeWarnVersion();

    byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    Operation[] operationArray = operations.getOperations();
    while (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
      Operation currentOperation;
      int opcode;
      int pc = frame.getPC();
      if (pc < code.length) {
        opcode = code[pc] & 0xff;
        currentOperation = operationArray[opcode];
      } else {
        opcode = 0;
        currentOperation = endOfScriptStop;
      }
      frame.setCurrentOperation(currentOperation);
      operationTracer.tracePreExecution(frame);

      OperationResult result;
      try {
        result =
            switch (opcode) {
              case 0x01 -> AddOperationV2.staticOperation(frame);
              case 0x02 -> MulOperationV2.staticOperation(frame);
              case 0x03 -> SubOperationV2.staticOperation(frame);
              case 0x04 -> DivOperationV2.staticOperation(frame);
              case 0x05 -> SDivOperationV2.staticOperation(frame);
              case 0x06 -> ModOperationV2.staticOperation(frame);
              case 0x07 -> SModOperationV2.staticOperation(frame);
              case 0x08 -> AddModOperationV2.staticOperation(frame);
              case 0x09 -> MulModOperationV2.staticOperation(frame);
              case 0x1b ->
                  enableConstantinople
                      ? ShlOperationV2.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1c ->
                  enableConstantinople
                      ? ShrOperationV2.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              case 0x1d ->
                  enableConstantinople
                      ? SarOperationV2.staticOperation(frame)
                      : InvalidOperation.invalidOperationResult(opcode);
              // TODO EVMv2: implement remaining opcodes in v2; until then fall through to v1
              default -> {
                frame.setCurrentOperation(currentOperation);
                yield currentOperation.execute(frame, this);
              }
            };
      } catch (final OverflowException oe) {
        result = OVERFLOW_RESPONSE;
      } catch (final UnderflowException ue) {
        result = UNDERFLOW_RESPONSE;
      }
      final ExceptionalHaltReason haltReason = result.getHaltReason();
      if (haltReason != null) {
        LOG.trace("MessageFrame evaluation halted because of {}", haltReason);
        frame.setExceptionalHaltReason(Optional.of(haltReason));
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      } else if (frame.decrementRemainingGas(result.getGasCost()) < 0) {
        frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      }
      if (frame.getState() == MessageFrame.State.CODE_EXECUTING) {
        final int currentPC = frame.getPC();
        final int opSize = result.getPcIncrement();
        frame.setPC(currentPC + opSize);
      }
      operationTracer.tracePostExecution(frame, result);
    }
  }

  /**
   * Get Operations (unsafe)
   *
   * @return Operations array
   */
  public Operation[] getOperationsUnsafe() {
    return operations.getOperations();
  }

  private OperationResult shiftOperation(
      final MessageFrame frame,
      final Function<MessageFrame, OperationResult> standard,
      final Function<MessageFrame, OperationResult> optimized) {
    return savmConfiguration.enableOptimizedOpcodes()
        ? optimized.apply(frame)
        : standard.apply(frame);
  }

  /**
   * Gets or creates code instance with a cached jump destination.
   *
   * @param codeHash the code hash
   * @param codeBytes the code bytes
   * @return the code instance with the cached jump destination
   */
  public Code getOrCreateCachedJumpDest(final Hash codeHash, final Bytes codeBytes) {
    checkNotNull(codeHash);

    Code result = jumpDestOnlyCodeCache.getIfPresent(codeHash);
    if (result == null) {
      result = new Code(codeBytes);
      jumpDestOnlyCodeCache.put(codeHash, result);
    }

    return result;
  }
}
