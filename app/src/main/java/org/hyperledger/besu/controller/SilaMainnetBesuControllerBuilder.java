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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.sila.ConsensusContext;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.blockcreation.MiningCoordinator;
import org.hyperledger.besu.sila.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;

import java.util.Optional;

/** The SilaMainnet besu controller builder. */
public class SilaMainnetBesuControllerBuilder extends BesuControllerBuilder {

  /** Default constructor. */
  public SilaMainnetBesuControllerBuilder() {}

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningConfiguration miningConfiguration,
      final SyncState syncState,
      final SilProtocolManager silProtocolManager) {

    return new NoopMiningCoordinator(miningConfiguration);
  }

  @Override
  protected ConsensusContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    return null;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new NoopPluginServiceFactory();
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return SilaMainnetProtocolSchedule.fromConfig(
        genesisConfigOptions,
        Optional.of(dataStorageConfiguration.getRevertReasonEnabled()),
        Optional.of(savmConfiguration),
        super.miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  @Override
  protected void prepForBuild() {
    // No special preparation needed for sila-mainnet
  }
}
