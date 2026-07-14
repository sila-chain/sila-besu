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
package org.hyperledger.besu.sila.sila-mainnet;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.difficulty.fixed.FixedDifficultyCalculators;
import org.hyperledger.besu.sila.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides {@link ProtocolSpec} lookups for sila-mainnet hard forks. */
public class SilaMainnetProtocolSchedule {

  private static final Logger LOG = LoggerFactory.getLogger(SilaMainnetProtocolSchedule.class);

  public static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  /**
   * Create a SilaMainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param savmConfiguration how to configure the SAVMs jumpdest cache
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled
   * @param balConfiguration configuration related to block access lists
   * @param metricsSystem A metricSystem instance to expose metrics in the underlying calls
   * @return A configured sila-mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final Optional<Boolean> isRevertReasonEnabled,
      final Optional<SavmConfiguration> savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    if (FixedDifficultyCalculators.isFixedDifficultyInConfig(config)) {
      LOG.warn(
          "Genesis config contains silash.fixedDifficulty. "
              + "This option is deprecated and will be removed in a future release.");
      return FixedDifficultyProtocolSchedule.create(
          config,
          isRevertReasonEnabled.orElse(false),
          savmConfiguration.orElse(SavmConfiguration.DEFAULT),
          miningConfiguration,
          badBlockManager,
          isParallelTxProcessingEnabled,
          balConfiguration,
          metricsSystem);
    }
    return new ProtocolScheduleBuilder(
            config,
            Optional.of(DEFAULT_CHAIN_ID),
            ProtocolSpecAdapters.create(0, Function.identity()),
            isRevertReasonEnabled.orElse(false),
            savmConfiguration.orElse(SavmConfiguration.DEFAULT),
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .createProtocolSchedule();
  }

  /**
   * Create a SilaMainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param isRevertReasonEnabled whether storing the revert reason is for failed transactions
   * @param savmConfiguration how to configure the SAVMs jumpdest cache
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured sila-mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.of(isRevertReasonEnabled),
        Optional.of(savmConfiguration),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  /**
   * Create a SilaMainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param savmConfiguration size of
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured sila-mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.empty(),
        Optional.of(savmConfiguration),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  /**
   * Create a SilaMainnet protocol schedule from a config object
   *
   * @param config {@link GenesisConfigOptions} containing the config options for the milestone
   *     starting points
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return A configured sila-mainnet protocol schedule
   */
  public static ProtocolSchedule fromConfig(
      final GenesisConfigOptions config,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return fromConfig(
        config,
        Optional.empty(),
        Optional.empty(),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }
}
