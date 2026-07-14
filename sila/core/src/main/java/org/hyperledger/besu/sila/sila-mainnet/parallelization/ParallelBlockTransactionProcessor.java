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
package org.hyperledger.besu.sila.sila-mainnet.parallelization;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.sila-mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;
import org.hyperledger.besu.sila.processing.TransactionProcessingResult;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.sila.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.savm.blockhash.BlockHashLookup;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class ParallelBlockTransactionProcessor {

  protected CompletableFuture<ParallelizedTransactionContext>[] futures;

  protected CompletableFuture<ParallelizedTransactionContext> removeFuture(final int txIndex) {
    final CompletableFuture<ParallelizedTransactionContext> future = futures[txIndex];
    futures[txIndex] = null;
    return future;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void runAsyncBlock(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Wei blobGasPrice,
      final Executor executor,
      final Optional<BlockAccessListBuilder> blockAccessListBuilder,
      final Optional<BlockHeader> maybeParentHeader) {

    futures = new CompletableFuture[transactions.size()];

    for (int i = 0; i < transactions.size(); i++) {
      final int txIndex = i;
      final Transaction transaction = transactions.get(i);

      futures[i] =
          CompletableFuture.supplyAsync(
              () ->
                  runTransaction(
                      protocolContext,
                      blockHeader,
                      txIndex,
                      transaction,
                      miningBeneficiary,
                      blockHashLookup,
                      blobGasPrice,
                      blockAccessListBuilder,
                      maybeParentHeader),
              executor);
    }
  }

  /** World state at the parent block. Call only when the parent header is known to be present. */
  protected Optional<BonsaiWorldState> getWorldState(
      final ProtocolContext protocolContext, final BlockHeader parentHeader) {
    return protocolContext
        .getWorldStateArchive()
        .getWorldState(WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(parentHeader))
        .map(BonsaiWorldState.class::cast);
  }

  protected abstract ParallelizedTransactionContext runTransaction(
      ProtocolContext protocolContext,
      BlockHeader blockHeader,
      int transactionLocation,
      Transaction transaction,
      Address miningBeneficiary,
      BlockHashLookup blockHashLookup,
      Wei blobGasPrice,
      Optional<BlockAccessListBuilder> blockAccessListBuilder,
      Optional<BlockHeader> maybeParentHeader);

  public abstract Optional<TransactionProcessingResult> getProcessingResult(
      MutableWorldState worldState,
      Address miningBeneficiary,
      Transaction transaction,
      int location,
      Optional<Counter> confirmedParallelizedTransactionCounter,
      Optional<Counter> conflictingButCachedTransactionCounter);
}
