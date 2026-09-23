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
package org.hyperledger.besu.savmtool;

import org.hyperledger.besu.metrics.MetricsConfigurationModule;
import org.hyperledger.besu.metrics.MetricsSystemModule;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.worldstate.WorldUpdater;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpec;
import org.hyperledger.besu.sila.trie.pathbased.common.code.PathBasedCodeCacheModule;

import javax.inject.Singleton;

import dagger.Component;

/**
 * This is a Dagger component interface for the SAVM (Sila Virtual Machine) tool. It is annotated
 * with @Singleton to ensure that only a single instance of this component exists within the Dagger
 * component graph.
 *
 * <p>The component is composed of several modules that provide the necessary dependencies for the
 * SAVM tool: - ProtocolModule: Provides the protocol specification. - GenesisFileModule: Provides
 * the genesis file for the blockchain. - DataStoreModule: Provides the data store for blockchain
 * data. - BlockchainModule: Provides the blockchain instance. - SavmToolCommandOptionsModule:
 * Provides the command options for the SAVM tool. - MetricsConfigurationModule and
 * MetricsSystemModule: Provide the metrics system and its configuration.
 *
 * <p>The interface defines methods to get instances of key classes like ProtocolSpec, SAVM,
 * WorldUpdater, MutableWorldState, and Blockchain. These methods are used by Dagger to inject the
 * returned instances where needed.
 */
@Singleton
@Component(
    modules = {
      ProtocolModule.class,
      GenesisFileModule.class,
      DataStoreModule.class,
      BlockchainModule.class,
      SavmToolCommandOptionsModule.class,
      MetricsConfigurationModule.class,
      MetricsSystemModule.class,
      PathBasedCodeCacheModule.class,
    })
public interface SavmToolComponent {

  /**
   * Retrieves the ProtocolSpec instance. ProtocolSpec defines the Sila protocol specifications,
   * which includes the precompiled contracts, the gas calculator, the SAVM, and the private nonce
   * calculator.
   *
   * @return The ProtocolSpec instance.
   */
  ProtocolSpec getProtocolSpec();

  /**
   * Retrieves the SAVM instance. SAVM (Sila Virtual Machine) is responsible for executing the
   * bytecode of smart contracts in Sila.
   *
   * @return The SAVM instance.
   */
  SAVM getSAVM();

  /**
   * Retrieves the WorldUpdater instance. WorldUpdater is used to modify the world state, which
   * includes the accounts and their associated storage and code.
   *
   * @return The WorldUpdater instance.
   */
  WorldUpdater getWorldUpdater();

  /**
   * Retrieves the MutableWorldState instance. MutableWorldState represents the world state of Sila,
   * which includes all accounts, their balances, nonces, codes, and storage.
   *
   * @return The MutableWorldState instance.
   */
  MutableWorldState getWorldState();

  /**
   * Retrieves the Blockchain instance. Blockchain represents the Sila blockchain, which includes
   * blocks, transactions, and the world state.
   *
   * @return The Blockchain instance.
   */
  Blockchain getBlockchain();
}
