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
package org.hyperledger.besu.sila.components;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetProtocolSpecs;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

/** Provides protocol specs for network forks. */
@Module
public class ProtocolSpecModule {

  /** Default constructor. */
  public ProtocolSpecModule() {}

  /**
   * Provides the protocol spec for the frontier network fork.
   *
   * @param genesisConfigOptions the genesis config options
   * @param savmConfiguration the SAVM configuration
   * @param isParallelTxEnabled whether parallel tx processing is enabled
   * @param metricsSystem the metrics system
   * @param balConfiguration configuration for block-level access lists
   * @return the protocol spec for the frontier network fork
   */
  @Provides
  @Named("frontier")
  public ProtocolSpecBuilder frontierProtocolSpec(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return SilaMainnetProtocolSpecs.frontierDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxEnabled,
        balConfiguration,
        metricsSystem);
  }
}
