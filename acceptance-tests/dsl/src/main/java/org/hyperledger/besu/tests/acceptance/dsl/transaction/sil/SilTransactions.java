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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.sil;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;

import java.math.BigInteger;

import sila.web3j.protocol.core.DefaultBlockParameter;
import sila.web3j.protocol.core.DefaultBlockParameterName;

public class SilTransactions {

  public SilBlockNumberTransaction blockNumber() {
    return new SilBlockNumberTransaction();
  }

  public SilGetBlockTransaction block() {
    return block(DefaultBlockParameterName.LATEST);
  }

  public SilGetBlockTransaction block(final DefaultBlockParameter blockParameter) {
    return new SilGetBlockTransaction(blockParameter, false);
  }

  public SilGetBalanceTransaction getBalance(final Account account) {
    return new SilGetBalanceTransaction(account);
  }

  public SilGetCodeTransaction getCode(final Account account) {
    return new SilGetCodeTransaction(account);
  }

  public SilGetStorageAtTransaction getStorageAt(final Account account, final BigInteger position) {
    return new SilGetStorageAtTransaction(account, position);
  }

  public SilGetBalanceAtBlockTransaction getBalanceAtBlock(
      final Account account, final BigInteger block) {
    return new SilGetBalanceAtBlockTransaction(account, block);
  }

  public SilAccountsTransaction accounts() {
    return new SilAccountsTransaction();
  }

  public SilGetTransactionReceiptTransaction getTransactionReceipt(final String transactionHash) {
    return new SilGetTransactionReceiptTransaction(transactionHash);
  }

  public SilSendRawTransactionTransaction sendRawTransaction(final String transactionData) {
    return new SilSendRawTransactionTransaction(transactionData);
  }

  public SilGetTransactionCountTransaction getTransactionCount(final String accountAddress) {
    return new SilGetTransactionCountTransaction(accountAddress);
  }

  public SilGetTransactionReceiptWithRevertReason getTransactionReceiptWithRevertReason(
      final String transactionHash) {
    return new SilGetTransactionReceiptWithRevertReason(transactionHash);
  }

  /**
   * Fetches a block with slotNumber field (SIP-7843, SilaAmsterdam+).
   *
   * @param blockNumber the block number as hex string (e.g., "0x1") or "latest"
   * @return the transaction to fetch the block with slotNumber
   */
  public SilGetBlockWithSlotNumber getBlockWithSlotNumber(final String blockNumber) {
    return new SilGetBlockWithSlotNumber(blockNumber);
  }

  public SilSyncingTransaction syncing() {
    return new SilSyncingTransaction();
  }

  public SilNewPendingTransactionFilterTransaction newPendingTransactionsFilter() {
    return new SilNewPendingTransactionFilterTransaction();
  }

  public SilFilterChangesTransaction filterChanges(final BigInteger filterId) {
    return new SilFilterChangesTransaction(filterId);
  }

  public SilCallTransaction call(final String contractAddress, final String functionCall) {
    return new SilCallTransaction(contractAddress, functionCall);
  }

  public SilCallTransaction call(final String contractAddress) {
    return new SilCallTransaction(contractAddress, "0x");
  }
}
