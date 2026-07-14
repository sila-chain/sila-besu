/*
 * Copyright 2020 ConsenSys AG.
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
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Optional;

public class SilaMainnetProtocolSpecFactory {

  private final Optional<BigInteger> chainId;
  private final boolean isRevertReasonEnabled;
  private final GenesisConfigOptions genesisConfigOptions;
  private final SavmConfiguration savmConfiguration;
  private final MiningConfiguration miningConfiguration;
  private final boolean isParallelTxProcessingEnabled;
  private final BalConfiguration balConfiguration;
  private final MetricsSystem metricsSystem;

  public SilaMainnetProtocolSpecFactory(
      final Optional<BigInteger> chainId,
      final boolean isRevertReasonEnabled,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    this.chainId = chainId;
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    this.genesisConfigOptions = genesisConfigOptions;
    this.savmConfiguration = savmConfiguration;
    this.miningConfiguration = miningConfiguration;
    this.isParallelTxProcessingEnabled = isParallelTxProcessingEnabled;
    this.balConfiguration = balConfiguration;
    this.metricsSystem = metricsSystem;
  }

  public ProtocolSpecBuilder frontierDefinition() {
    return SilaMainnetProtocolSpecs.frontierDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder homesteadDefinition() {
    return SilaMainnetProtocolSpecs.homesteadDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder daoRecoveryInitDefinition() {
    return SilaMainnetProtocolSpecs.daoRecoveryInitDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder daoRecoveryTransitionDefinition() {
    return SilaMainnetProtocolSpecs.daoRecoveryTransitionDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder tangerineWhistleDefinition() {
    return SilaMainnetProtocolSpecs.tangerineWhistleDefinition(
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder spuriousDragonDefinition() {
    return SilaMainnetProtocolSpecs.spuriousDragonDefinition(
        chainId,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder byzantiumDefinition() {
    return SilaMainnetProtocolSpecs.byzantiumDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder constantinopleDefinition() {
    return SilaMainnetProtocolSpecs.constantinopleDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder petersburgDefinition() {
    return SilaMainnetProtocolSpecs.petersburgDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder istanbulDefinition() {
    return SilaMainnetProtocolSpecs.istanbulDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder muirGlacierDefinition() {
    return SilaMainnetProtocolSpecs.muirGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder berlinDefinition() {
    return SilaMainnetProtocolSpecs.berlinDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder londonDefinition() {
    return SilaMainnetProtocolSpecs.londonDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder arrowGlacierDefinition() {
    return SilaMainnetProtocolSpecs.arrowGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder grayGlacierDefinition() {
    return SilaMainnetProtocolSpecs.grayGlacierDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder parisDefinition() {
    return SilaMainnetProtocolSpecs.parisDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder shanghaiDefinition() {
    return SilaMainnetProtocolSpecs.shanghaiDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder cancunDefinition() {
    return SilaMainnetProtocolSpecs.cancunDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder pragueDefinition() {
    return SilaMainnetProtocolSpecs.pragueDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder osakaDefinition() {
    return SilaMainnetProtocolSpecs.osakaDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder bpo1Definition() {
    return SilaMainnetProtocolSpecs.bpo1Definition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder bpo2Definition() {
    return SilaMainnetProtocolSpecs.bpo2Definition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder bpo3Definition() {
    return SilaMainnetProtocolSpecs.bpo3Definition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder bpo4Definition() {
    return SilaMainnetProtocolSpecs.bpo4Definition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder bpo5Definition() {
    return SilaMainnetProtocolSpecs.bpo5Definition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  public ProtocolSpecBuilder amsterdamDefinition() {
    return SilaMainnetProtocolSpecs.amsterdamDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  /**
   * The "future" fork consists of SIPs that have been approved for Sila SilaMainnet but not
   * scheduled for a fork. This is also known as "Eligible For Inclusion" (EFI) or "Considered for
   * Inclusion" (CFI).
   *
   * <p>There is no guarantee of the contents of this fork across Besu releases and should be
   * considered unstable.
   *
   * @return a protocol spec for the "Future" fork.
   */
  public ProtocolSpecBuilder futureSipsDefinition() {
    return SilaMainnetProtocolSpecs.futureSipsDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  /**
   * The "experimental" fork consists of SIPs and other changes that have not been approved for any
   * fork but are implemented in Besu, either for demonstration or experimentation.
   *
   * <p>There is no guarantee of the contents of this fork across Besu releases and should be
   * considered unstable.
   *
   * @return a protocol spec for the "Experimental" fork.
   */
  public ProtocolSpecBuilder experimentalSipsDefinition() {
    return SilaMainnetProtocolSpecs.experimentalSipsDefinition(
        chainId,
        isRevertReasonEnabled,
        genesisConfigOptions,
        savmConfiguration,
        miningConfiguration,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }
}
