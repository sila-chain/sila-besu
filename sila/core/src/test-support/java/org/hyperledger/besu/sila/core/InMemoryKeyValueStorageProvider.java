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
package org.hyperledger.besu.sila.core;

import static org.hyperledger.besu.sila.core.WorldStateHealerHelper.throwingWorldStateHealerSupplier;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.DefaultBlockchain;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.chain.VariablesStorage;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetBlockHeaderFunctions;
import org.hyperledger.besu.sila.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.sila.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.sila.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.sila.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.sila.trie.forest.ForestWorldStateArchive;
import org.hyperledger.besu.sila.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.sila.trie.forest.worldview.ForestMutableWorldState;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.worldview.accumulator.preload.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.sila.trie.pathbased.common.code.PathBasedCodeCache;
import org.hyperledger.besu.sila.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.sila.worldstate.WorldStateStorageCoordinator;

public class InMemoryKeyValueStorageProvider extends KeyValueStorageProvider {

  public InMemoryKeyValueStorageProvider() {
    super(
        segmentIdentifiers -> new SegmentedInMemoryKeyValueStorage(),
        new InMemoryKeyValueStorage(),
        new NoOpMetricsSystem());
  }

  public static MutableBlockchain createInMemoryBlockchain(final Block genesisBlock) {
    return createInMemoryBlockchain(genesisBlock, createInMemoryVariablesStorage());
  }

  public static MutableBlockchain createInMemoryBlockchain(
      final Block genesisBlock, final VariablesStorage variablesStorage) {
    return createInMemoryBlockchain(
        genesisBlock, new SilaMainnetBlockHeaderFunctions(), variablesStorage);
  }

  public static MutableBlockchain createInMemoryBlockchain(
      final Block genesisBlock, final BlockHeaderFunctions blockHeaderFunctions) {
    return createInMemoryBlockchain(
        genesisBlock, blockHeaderFunctions, createInMemoryVariablesStorage());
  }

  public static MutableBlockchain createInMemoryBlockchain(
      final Block genesisBlock,
      final BlockHeaderFunctions blockHeaderFunctions,
      final VariablesStorage variablesStorage) {
    final InMemoryKeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    return DefaultBlockchain.createMutable(
        genesisBlock,
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            keyValueStorage, variablesStorage, blockHeaderFunctions, false),
        new NoOpMetricsSystem(),
        0);
  }

  public static ForestWorldStateArchive createInMemoryWorldStateArchive() {
    return new ForestWorldStateArchive(
        new WorldStateStorageCoordinator(
            new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage())),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()),
        SavmConfiguration.DEFAULT);
  }

  public static BonsaiWorldStateProvider createBonsaiInMemoryWorldStateArchive(
      final Blockchain blockchain) {
    return createBonsaiInMemoryWorldStateArchive(blockchain, SavmConfiguration.DEFAULT, null);
  }

  public static BonsaiWorldStateProvider createBonsaiInMemoryWorldStateArchive(
      final Blockchain blockchain, final ServiceManager serviceManager) {
    return createBonsaiInMemoryWorldStateArchive(
        blockchain, SavmConfiguration.DEFAULT, serviceManager);
  }

  public static BonsaiWorldStateProvider createBonsaiInMemoryWorldStateArchive(
      final Blockchain blockchain,
      final SavmConfiguration savmConfiguration,
      final ServiceManager serviceManager) {
    final InMemoryKeyValueStorageProvider inMemoryKeyValueStorageProvider =
        new InMemoryKeyValueStorageProvider();
    final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader =
        new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem());
    return new BonsaiWorldStateProvider(
        (BonsaiWorldStateKeyValueStorage)
            inMemoryKeyValueStorageProvider.createWorldStateStorage(
                DataStorageConfiguration.DEFAULT_BONSAI_CONFIG),
        blockchain,
        DataStorageConfiguration.DEFAULT_BONSAI_CONFIG.getPathBasedExtraStorageConfiguration(),
        bonsaiCachedMerkleTrieLoader,
        serviceManager,
        savmConfiguration,
        throwingWorldStateHealerSupplier(),
        new PathBasedCodeCache());
  }

  public static MutableWorldState createInMemoryWorldState() {
    final InMemoryKeyValueStorageProvider provider = new InMemoryKeyValueStorageProvider();
    return new ForestMutableWorldState(
        provider.createWorldStateStorage(DataStorageConfiguration.DEFAULT_FOREST_CONFIG),
        provider.createWorldStatePreimageStorage(),
        SavmConfiguration.DEFAULT);
  }

  public static VariablesStorage createInMemoryVariablesStorage() {
    return new VariablesKeyValueStorage(new InMemoryKeyValueStorage());
  }
}
