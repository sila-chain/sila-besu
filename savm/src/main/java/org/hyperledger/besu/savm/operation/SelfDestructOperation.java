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
package org.hyperledger.besu.savm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.account.Account;
import org.hyperledger.besu.savm.account.MutableAccount;
import org.hyperledger.besu.savm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.savm.frame.MessageFrame;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.internal.Words;
import org.hyperledger.besu.savm.log.TransferLogEmitter;

/** The Self destruct operation. */
public class SelfDestructOperation extends AbstractOperation {

  final boolean sip6780Semantics;
  final TransferLogEmitter transferLogEmitter;

  /**
   * Instantiates a new Self destruct operation.
   *
   * @param gasCalculator the gas calculator
   */
  public SelfDestructOperation(final GasCalculator gasCalculator) {
    this(gasCalculator, false, TransferLogEmitter.NOOP);
  }

  /**
   * Instantiates a new Self destruct operation, with an optional SIP-6780 semantics flag. SIP-6780
   * will only remove an account if the account was created within the current transaction. All
   * other semantics remain.
   *
   * @param gasCalculator the gas calculator
   * @param sip6780Semantics Enforce SIP6780 semantics.
   */
  public SelfDestructOperation(final GasCalculator gasCalculator, final boolean sip6780Semantics) {
    this(gasCalculator, sip6780Semantics, TransferLogEmitter.NOOP);
  }

  /**
   * Instantiates a new Self destruct operation with SIP-6780 and transfer log emission support.
   *
   * @param gasCalculator the gas calculator
   * @param sip6780Semantics Enforce SIP-6780 semantics (only destroy if created in same tx).
   * @param transferLogEmitter strategy for emitting transfer logs.
   */
  public SelfDestructOperation(
      final GasCalculator gasCalculator,
      final boolean sip6780Semantics,
      final TransferLogEmitter transferLogEmitter) {
    super(0xFF, "SELFDESTRUCT", 1, 0, gasCalculator);
    this.sip6780Semantics = sip6780Semantics;
    this.transferLogEmitter = transferLogEmitter;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final SAVM savm) {

    // checking for static violations first means fewer account accesses
    if (frame.isStatic()) {
      return new OperationResult(0, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    // First calculate cost.  There's a bit of yak shaving getting values to calculate the cost.
    final Address beneficiaryAddress = Words.toAddress(frame.popStackItem());
    final boolean beneficiaryIsWarm =
        frame.warmUpAddress(beneficiaryAddress) || gasCalculator().isPrecompile(beneficiaryAddress);
    final long beneficiaryAccessCost =
        beneficiaryIsWarm ? 0L : gasCalculator().getColdAccountAccessCost();
    final long staticCost =
        gasCalculator().selfDestructOperationStaticGasCost() + beneficiaryAccessCost;

    if (frame.getRemainingGas() < staticCost) {
      return new OperationResult(staticCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    final Account beneficiaryNullable = getAccount(beneficiaryAddress, frame);
    final Address originatorAddress = frame.getRecipientAddress();
    final MutableAccount originatorAccount = getMutableAccount(originatorAddress, frame);
    final Wei originatorBalance = originatorAccount.getBalance();

    final long cost =
        gasCalculator().selfDestructOperationGasCost(beneficiaryNullable, originatorBalance)
            + beneficiaryAccessCost;

    // With the cost we can test for out-of-gas early WithdrawalRequests
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // SIP-8037: Deduct regular gas before charging state gas (ordering requirement).
    frame.decrementRemainingGas(cost);

    // SIP-8037: Charge state gas when SELFDESTRUCT forces creation of an empty beneficiary.
    if ((beneficiaryNullable == null || beneficiaryNullable.isEmpty())
        && !originatorBalance.isZero()
        && !frame.consumeStateGas(gasCalculator().stateGasCostCalculator().newAccountStateGas())) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Add regular gas back — the SAVM loop will deduct it via the OperationResult.
    frame.incrementRemainingGas(cost);

    // We passed preliminary checks, get mutable accounts.
    final MutableAccount beneficiaryAccount = getOrCreateAccount(beneficiaryAddress, frame);

    // Determine if the account will actually be destroyed (pre-SilaCancun or same-tx-create)
    // or if only a SENDALL will be executed
    final boolean willBeDestroyed =
        !sip6780Semantics || frame.wasCreatedInTransaction(originatorAccount.getAddress());

    // Do the "sweep," all modes send all originator balance to the beneficiary account.
    originatorAccount.decrementBalance(originatorBalance);
    beneficiaryAccount.incrementBalance(originatorBalance);

    // SIP-7708: if the contract will actually be destroyed, and it is not a self transfer emit
    // a burn log or a transfer log depending on if it's a self transfer or not
    if (!originatorAddress.equals(beneficiaryAddress) || willBeDestroyed) {
      transferLogEmitter.emitSelfDestructLog(
          frame, originatorAddress, beneficiaryAddress, originatorBalance);
    }

    // If we are actually destroying the originator (pre-SilaCancun or same-tx-create) we need to
    // explicitly zero out the account balance (destroying siler/value if the originator is the
    // beneficiary) as well as tag it for later self-destruct cleanup.
    if (willBeDestroyed) {
      frame.addSelfDestruct(originatorAccount.getAddress());
      originatorAccount.setBalance(Wei.ZERO);
    }

    // Add refund in message frame.
    frame.addRefund(beneficiaryAddress, originatorBalance);

    // Set frame to CODE_SUCCESS so that the frame performs a normal halt.
    frame.setState(MessageFrame.State.CODE_SUCCESS);

    return new OperationResult(cost, null);
  }
}
