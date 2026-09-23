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
package org.hyperledger.besu.sila.trie.pathbased.bonsai.worldview.bal;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.savm.account.MutableAccount;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.silaMainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.sila.silaMainnet.block.access.list.BlockAccessListChanges;
import org.hyperledger.besu.sila.silaMainnet.staterootcommitter.BalRootComputation;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.sila.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.sila.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.sila.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.sila.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("rawtypes")
public class BlockAccessListStateRootCalculator {

  private BlockAccessListStateRootCalculator() {}

  public static CompletableFuture<BalRootComputation> computeAsync(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final BlockAccessList bal) {
    return computeAsync(protocolContext, blockHeader, bal, ForkJoinPool.commonPool());
  }

  public static CompletableFuture<BalRootComputation> computeAsync(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final BlockAccessList bal,
      final Executor executor) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (BonsaiWorldState ws = openParentWorldState(protocolContext, blockHeader)) {
            applyBalChanges(ws.getAccumulator(), bal);
            return computeRoot(ws);
          }
        },
        executor);
  }

  private static BonsaiWorldState openParentWorldState(
      final ProtocolContext protocolContext, final BlockHeader blockHeader) {
    final Hash parentHash = blockHeader.getParentHash();
    final BlockHeader parentHeader =
        protocolContext
            .getBlockchain()
            .getBlockHeader(parentHash)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Parent %s of block %s not found",
                            parentHash, blockHeader.getBlockHash())));
    final BonsaiWorldState ws =
        (BonsaiWorldState)
            protocolContext
                .getWorldStateArchive()
                .getWorldState(
                    WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(parentHeader))
                .orElseThrow();
    ws.disableCacheMerkleTrieLoader();
    return ws;
  }

  private static void applyBalChanges(
      final PathBasedWorldStateUpdateAccumulator accumulator, final BlockAccessList bal) {
    for (final var changes : BlockAccessListChanges.latestChanges(bal)) {
      final Address address = changes.address();
      final MutableAccount account = accumulator.getOrCreate(address);

      changes.balance().ifPresent(account::setBalance);
      changes.nonce().ifPresent(account::setNonce);
      changes.code().ifPresent(account::setCode);

      for (final var storage : changes.storageChanges()) {
        storage.slot().getSlotKey().ifPresent(key -> account.setStorageValue(key, storage.value()));
      }
    }
    accumulator.clearAccountsThatAreEmpty();
    accumulator.commit();
  }

  private static BalRootComputation computeRoot(final PathBasedWorldState worldState) {
    final PathBasedWorldStateUpdateAccumulator accumulator = worldState.getAccumulator();
    final PathBasedWorldStateKeyValueStorage.Updater updater =
        worldState.getWorldStateStorage().updater();
    final Hash root = worldState.calculateRootHash(Optional.of(updater), accumulator);
    updater.commit();
    return new BalRootComputation(root, accumulator);
  }
}
