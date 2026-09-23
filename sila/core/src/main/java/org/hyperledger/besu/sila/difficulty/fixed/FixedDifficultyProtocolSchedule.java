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
package org.hyperledger.besu.sila.difficulty.fixed;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecAdapters;

import java.util.Optional;

/**
 * A ProtocolSchedule which behaves similarly to pre-merge MainNet, but with a much reduced
 * difficulty.
 */
public class FixedDifficultyProtocolSchedule {

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return new ProtocolScheduleBuilder(
            config,
            Optional.empty(),
            ProtocolSpecAdapters.create(
                0,
                builder ->
                    builder.difficultyCalculator(FixedDifficultyCalculators.calculator(config))),
            isRevertReasonEnabled,
            savmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return create(
        config,
        false,
        savmConfiguration,
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }
}
