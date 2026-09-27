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
package org.hyperledger.besu.consensus.merge;

import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.PARIS;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.savm.SilaMainnetEVMs;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.GasLimitCalculator;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BlockHeaderValidator;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.sila.silaMainnet.feemarket.FeeMarket;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The Merge protocol schedule. */
public class MergeProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(1);

  /** Default constructor. */
  MergeProtocolSchedule() {}

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @param savmConfiguration the savm configuration
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem,
      final SavmConfiguration savmConfiguration) {

    Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> postMergeModifications =
        new HashMap<>();
    postMergeModifications.put(
        0L,
        (specBuilder) ->
            MergeProtocolSchedule.applyParisSpecificModifications(
                specBuilder, config.getChainId(), miningConfiguration, savmConfiguration));
    unapplyModificationsFromShanghaiOnwards(config, postMergeModifications);

    return new ProtocolScheduleBuilder(
            config,
            Optional.of(DEFAULT_CHAIN_ID),
            new ProtocolSpecAdapters(postMergeModifications),
            isRevertReasonEnabled,
            savmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .createProtocolSchedule();
  }

  /**
   * Apply SilaParis specific modifications because the Merge Transition code does not utilise
   * {@link org.hyperledger.besu.sila.silaMainnet.SilaMainnetProtocolSpecFactory.parisDefinition}
   * until the shanghaiDefinition is utilised. This is due to the way the Transition works via TTD
   * rather than via a blockNumber so it can't be looked up in the schedule.
   */
  private static ProtocolSpecBuilder applyParisSpecificModifications(
      final ProtocolSpecBuilder specBuilder,
      final Optional<BigInteger> chainId,
      final MiningConfiguration miningConfiguration,
      final SavmConfiguration savmConfiguration) {

    return specBuilder
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetEVMs.paris(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .blockHeaderValidatorBuilder(MergeProtocolSchedule::getBlockHeaderValidator)
        .blockReward(Wei.ZERO)
        .difficultyCalculator((a, b) -> BigInteger.ZERO)
        .skipZeroBlockRewards(true)
        .isPoS(true)
        .slotDuration(Duration.ofSeconds(miningConfiguration.getUnstable().getPosSlotDuration()))
        .hardforkId(PARIS);
  }

  private static BlockHeaderValidator.Builder getBlockHeaderValidator(
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator) {
    return MergeValidationRulesetFactory.mergeBlockHeaderValidator(feeMarket);
  }

  private static void unapplyModificationsFromShanghaiOnwards(
      final GenesisConfigOptions config,
      final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> postMergeModifications) {
    // Any post-SilaParis fork can rely on the SilaMainnetProtocolSpec definitions again
    // Must allow for config to skip SilaShanghai and go straight to a later fork.
    if (!config.getForkBlockTimestamps().isEmpty()) {
      postMergeModifications.put(config.getForkBlockTimestamps().getFirst(), Function.identity());
    }
  }
}
