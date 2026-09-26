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
package org.hyperledger.besu.sila.transaction.pluginadapter;

import static org.hyperledger.besu.sila.silaMainnet.TransactionValidationParams.transactionSimulator;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.savm.tracing.OperationTracer;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.processing.TransactionProcessingResult;
import org.hyperledger.besu.sila.silaMainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.sila.silaMainnet.TransactionValidationParams;
import org.hyperledger.besu.sila.silaMainnet.ValidationResult;
import org.hyperledger.besu.sila.transaction.CallParameter;
import org.hyperledger.besu.sila.transaction.TransactionInvalidReason;
import org.hyperledger.besu.sila.transaction.TransactionSimulator;

import java.util.EnumSet;
import java.util.Optional;

/** TransactionSimulationServiceImpl */
@Unstable
public class TransactionSimulationServiceImpl implements TransactionSimulationService {
  private Blockchain blockchain;
  private TransactionSimulator transactionSimulator;

  /** Create an instance to be configured */
  public TransactionSimulationServiceImpl() {}

  /**
   * Configure the service
   *
   * @param blockchain the blockchain
   * @param transactionSimulator transaction simulator
   */
  public void init(final Blockchain blockchain, final TransactionSimulator transactionSimulator) {
    this.blockchain = blockchain;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public ProcessableBlockHeader simulatePendingBlockHeader() {
    return transactionSimulator.simulatePendingBlockHeader();
  }

  @Override
  public Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final Hash blockHash,
      final OperationTracer operationTracer,
      final EnumSet<SimulationParameters> simulationParameters) {

    final CallParameter callParameter = CallParameter.fromTransaction(transaction);

    final var maybeBlockHeader =
        blockchain.getBlockHeader(blockHash).or(() -> blockchain.getBlockHeaderSafe(blockHash));

    if (maybeBlockHeader.isEmpty()) {
      return Optional.of(
          new TransactionSimulationResult(
              transaction,
              TransactionProcessingResult.invalid(
                  ValidationResult.invalid(TransactionInvalidReason.BLOCK_NOT_FOUND))));
    }

    return transactionSimulator
        .process(
            callParameter,
            maybeStateOverrides,
            simulationParameters2TransactionValidationParams(simulationParameters),
            operationTracer,
            maybeBlockHeader.get())
        .map(res -> new TransactionSimulationResult(transaction, res.result()));
  }

  @Override
  public Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final ProcessableBlockHeader pendingBlockHeader,
      final OperationTracer operationTracer,
      final EnumSet<SimulationParameters> simulationParameters) {

    final CallParameter callParameter = CallParameter.fromTransaction(transaction);

    return simulate(
        callParameter,
        maybeStateOverrides,
        pendingBlockHeader,
        operationTracer,
        simulationParameters);
  }

  @Override
  public Optional<TransactionSimulationResult> simulate(
      final org.hyperledger.besu.datatypes.CallParameter callParameter,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final ProcessableBlockHeader processableBlockHeader,
      final OperationTracer operationTracer,
      final EnumSet<SimulationParameters> simulationParameters) {

    return simulate(
        callParameter,
        maybeStateOverrides,
        processableBlockHeader,
        operationTracer,
        simulationParameters2TransactionValidationParams(simulationParameters));
  }

  private Optional<TransactionSimulationResult> simulate(
      final org.hyperledger.besu.datatypes.CallParameter callParameter,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final ProcessableBlockHeader processableBlockHeader,
      final OperationTracer operationTracer,
      final TransactionValidationParams txValidationParams) {

    return transactionSimulator
        .processOnPending(
            callParameter,
            maybeStateOverrides,
            txValidationParams,
            operationTracer,
            (org.hyperledger.besu.sila.core.ProcessableBlockHeader) processableBlockHeader)
        .map(res -> new TransactionSimulationResult(res.transaction(), res.result()));
  }

  private static TransactionValidationParams simulationParameters2TransactionValidationParams(
      final EnumSet<SimulationParameters> simulationParameters) {
    return ImmutableTransactionValidationParams.of(
        simulationParameters.contains(SimulationParameters.ALLOW_FUTURE_NONCE),
        simulationParameters.contains(SimulationParameters.ALLOW_EXCEEDING_BALANCE),
        simulationParameters.contains(SimulationParameters.ALLOW_UNDERPRICED),
        false,
        false,
        true,
        true,
        false);
  }
}
