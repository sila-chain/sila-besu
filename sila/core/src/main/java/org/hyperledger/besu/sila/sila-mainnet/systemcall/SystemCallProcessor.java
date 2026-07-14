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
package org.hyperledger.besu.sila.sila-mainnet.systemcall;

import static org.hyperledger.besu.savm.frame.MessageFrame.DEFAULT_MAX_STACK_SIZE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.core.ProcessableBlockHeader;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetTransactionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.savm.Code;
import org.hyperledger.besu.savm.account.Account;
import org.hyperledger.besu.savm.blockhash.BlockHashLookup;
import org.hyperledger.besu.savm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.savm.frame.MessageFrame;
import org.hyperledger.besu.savm.gascalculator.StateGasCostCalculator;
import org.hyperledger.besu.savm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.savm.tracing.OperationTracer;
import org.hyperledger.besu.savm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemCallProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(SystemCallProcessor.class);

  /**
   * The gas limit as defined in <a
   * href="https://sips.sila.org/SIPS/sip-2935#block-processing">SIP-2935</a> This value is
   * independent of the gas limit of the block
   */
  private static final long SYSTEM_CALL_GAS_LIMIT = 30_000_000L;

  /**
   * SIP-8037: maximum number of SSTOREs the state-gas reservoir of a system call must be sized to
   * cover. The reservoir gets {@code STATE_BYTES_PER_STORAGE_SLOT * cpsb *
   * SYSTEM_MAX_SSTORES_PER_CALL} extra gas on top of {@link #SYSTEM_CALL_GAS_LIMIT}.
   */
  private static final long SYSTEM_MAX_SSTORES_PER_CALL = 16L;

  /** The system address */
  static final Address SYSTEM_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  private final SilaMainnetTransactionProcessor sila-mainnetTransactionProcessor;

  public SystemCallProcessor(final SilaMainnetTransactionProcessor sila-mainnetTransactionProcessor) {
    this.sila-mainnetTransactionProcessor = sila-mainnetTransactionProcessor;
  }

  /**
   * Processes a system call.
   *
   * @param callAddress The address to call.
   * @param context The system call context. The input data to the system call.
   * @param inputData The input data to the system call.
   * @return The output of the system call.
   */
  public Bytes process(
      final Address callAddress,
      final BlockProcessingContext context,
      final Bytes inputData,
      final Optional<AccessLocationTracker> accessLocationTracker) {
    WorldUpdater blockUpdater = context.getWorldState().updater();
    WorldUpdater systemCallUpdater = blockUpdater.updater();
    final Account maybeContract = systemCallUpdater.get(callAddress);
    if (maybeContract == null) {
      throw new SystemCallNoCodeAtAddressException("Invalid system call address: " + callAddress);
    }
    if (maybeContract.getCode().isEmpty()) {
      throw new SystemCallNoCodeAtAddressException(
          "Invalid system call, no code at address " + callAddress);
    }

    final AbstractMessageProcessor processor =
        sila-mainnetTransactionProcessor.getMessageProcessor(MessageFrame.Type.MESSAGE_CALL);
    final MessageFrame frame =
        createMessageFrame(
            callAddress,
            systemCallUpdater,
            context.getBlockHeader(),
            context.getBlockHashLookup(),
            inputData,
            accessLocationTracker);

    Deque<MessageFrame> stack = frame.getMessageFrameStack();
    while (!stack.isEmpty()) {
      processor.process(stack.peekFirst(), OperationTracer.NO_TRACING);
    }

    accessLocationTracker.ifPresent(
        tracker ->
            context
                .getBlockAccessListBuilder()
                .ifPresent(builder -> builder.apply(tracker, systemCallUpdater)));

    if (frame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      systemCallUpdater.commit();
      blockUpdater.commit();
      return frame.getOutputData();
    }

    // The call must execute to completion
    LOG.error(
        "System call did not execute to completion - haltReason: {}, address: {}, frame state: {}",
        frame.getExceptionalHaltReason().orElse(ExceptionalHaltReason.NONE),
        callAddress,
        frame.getState());
    String errorMessage =
        frame
            .getExceptionalHaltReason()
            .map(haltReason -> "System call halted: " + haltReason.getDescription())
            .orElse("System call did not execute to completion");
    throw new RuntimeException(errorMessage);
  }

  private MessageFrame createMessageFrame(
      final Address callAddress,
      final WorldUpdater worldUpdater,
      final ProcessableBlockHeader blockHeader,
      final BlockHashLookup blockHashLookup,
      final Bytes inputData,
      final Optional<AccessLocationTracker> maybeAccessLocationTracker) {

    final AbstractMessageProcessor processor =
        sila-mainnetTransactionProcessor.getMessageProcessor(MessageFrame.Type.MESSAGE_CALL);

    MessageFrame.Builder builder =
        MessageFrame.builder()
            .maxStackSize(DEFAULT_MAX_STACK_SIZE)
            .worldUpdater(worldUpdater)
            .initialGas(SYSTEM_CALL_GAS_LIMIT)
            .originator(SYSTEM_ADDRESS)
            .gasPrice(Wei.ZERO)
            .blobGasPrice(Wei.ZERO)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .blockValues(blockHeader)
            .completer(__ -> {})
            .miningBeneficiary(Address.ZERO) // Confirm this
            .type(MessageFrame.Type.MESSAGE_CALL)
            .address(callAddress)
            .contract(callAddress)
            .inputData(inputData)
            .sender(SYSTEM_ADDRESS)
            .blockHashLookup(blockHashLookup)
            // SIP-8037: seed the state-gas reservoir so state-gas growth alone cannot OOG the call.
            // The reservoir is sized to cover up to SYSTEM_MAX_SSTORES_PER_CALL storage-set
            // charges.
            // Pre-SilaAmsterdam storageSetStateGas() is 0, so this seeds an empty reservoir (no
            // effect).
            .initialStateGasReservoir(systemCallStateGasReservoir())
            .code(getCode(worldUpdater.get(callAddress), processor));

    maybeAccessLocationTracker.ifPresent(
        tracker -> {
          builder.sip7928AccessList(tracker);
          tracker.addTouchedAccount(callAddress);
        });

    return builder.build();
  }

  private long systemCallStateGasReservoir() {
    final StateGasCostCalculator stateGasCalc =
        sila-mainnetTransactionProcessor.getGasCalculator().stateGasCostCalculator();
    return stateGasCalc.storageSetStateGas() * SYSTEM_MAX_SSTORES_PER_CALL;
  }

  private Code getCode(final Account contract, final AbstractMessageProcessor processor) {
    if (contract == null) {
      return Code.EMPTY_CODE;
    }

    // Bonsai accounts may have a fully cached code, so we use that one
    if (contract.getCodeCache() != null) {
      return contract.getOrCreateCachedCode();
    }

    // Any other account can only use the cached jump dest analysis if available
    return processor.getOrCreateCachedJumpDest(contract.getCodeHash(), contract.getCode());
  }
}
