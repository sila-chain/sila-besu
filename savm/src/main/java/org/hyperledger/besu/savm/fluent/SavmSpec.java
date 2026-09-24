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
package org.hyperledger.besu.savm.fluent;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.SilaMainnetEVMs;
import org.hyperledger.besu.savm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.savm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.savm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.errorprone.annotations.InlineMe;
import org.apache.tuweni.bytes.Bytes;

/** Specification for the SAVM execution. */
public final class SavmSpec {
  private final SAVM savm;
  private PrecompileContractRegistry precompileContractRegistry;
  private List<ContractValidationRule> contractValidationRules =
      List.of(
          MaxCodeSizeRule.from(SavmSpecVersion.SPURIOUS_DRAGON, SavmConfiguration.DEFAULT),
          PrefixCodeRule.of());
  private long initialNonce = 1;
  private boolean requireDeposit = true;
  private Collection<Address> forceCommitAddresses = List.of(Address.fromHexString("0x03"));

  private SavmSpec(final SAVM savm) {
    this.savm = savm;
  }

  /**
   * Create an SAVM spec with the most current activated fork on chain ID 1.
   *
   * <p>Note, this will change across versions
   *
   * @return savm spec builder
   */
  public static SavmSpec savmSpec() {
    return savmSpec(SavmSpecVersion.mostRecent());
  }

  /**
   * Create an SAVM spec at the specified version with chain ID 1.
   *
   * @param fork the SAVM spec version to use
   * @return savm spec builder
   */
  public static SavmSpec savmSpec(final SavmSpecVersion fork) {
    return savmSpec(fork, BigInteger.ONE);
  }

  /**
   * Create an SAVM spec at the specified version and chain ID
   *
   * @param fork the SAVM spec version to use
   * @param chainId the chain ID to use
   * @return savm spec builder
   */
  public static SavmSpec savmSpec(final SavmSpecVersion fork, final BigInteger chainId) {
    return savmSpec(fork, chainId, SavmConfiguration.DEFAULT);
  }

  /**
   * Create an SAVM spec at the specified version and chain ID
   *
   * @param fork the SAVM spec version to use
   * @param chainId the chain ID to use
   * @return savm spec builder
   */
  public static SavmSpec savmSpec(final SavmSpecVersion fork, final Bytes chainId) {
    return savmSpec(fork, new BigInteger(1, chainId.toArrayUnsafe()), SavmConfiguration.DEFAULT);
  }

  /**
   * Create an SAVM spec at the specified version and chain ID
   *
   * @param fork the SAVM spec version to use
   * @param chainId the chain ID to use
   * @param savmConfiguration system configuration options.
   * @return savm spec builder
   */
  public static SavmSpec savmSpec(
      final SavmSpecVersion fork,
      final BigInteger chainId,
      final SavmConfiguration savmConfiguration) {
    return switch (fork) {
      case FRONTIER -> frontier(savmConfiguration);
      case HOMESTEAD -> homestead(savmConfiguration);
      case TANGERINE_WHISTLE -> tangerineWhistle(savmConfiguration);
      case SPURIOUS_DRAGON -> spuriousDragon(savmConfiguration);
      case BYZANTIUM -> byzantium(savmConfiguration);
      case CONSTANTINOPLE -> constantinople(savmConfiguration);
      case PETERSBURG -> petersburg(savmConfiguration);
      case ISTANBUL -> istanbul(chainId, savmConfiguration);
      case BERLIN -> berlin(chainId, savmConfiguration);
      case LONDON -> london(chainId, savmConfiguration);
      case PARIS -> paris(chainId, savmConfiguration);
      case SHANGHAI -> shanghai(chainId, savmConfiguration);
      case CANCUN -> cancun(chainId, savmConfiguration);
      case PRAGUE -> prague(chainId, savmConfiguration);
      case OSAKA -> osaka(chainId, savmConfiguration);
      case AMSTERDAM -> amsterdam(chainId, savmConfiguration);
      case BOGOTA -> bogota(chainId, savmConfiguration);
      case POLIS -> polis(chainId, savmConfiguration);
      case BANGKOK -> bangkok(chainId, savmConfiguration);
      case FUTURE_EIPS -> futureEips(chainId, savmConfiguration);
      case EXPERIMENTAL_EIPS -> experimentalEips(chainId, savmConfiguration);
    };
  }

  /**
   * Instantiate Savm spec.
   *
   * @param savm the savm
   * @return the savm spec
   */
  public static SavmSpec savmSpec(final SAVM savm) {
    checkNotNull(savm, "savm must not be null");
    return new SavmSpec(savm);
  }

  /**
   * Instantiate Frontier savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec frontier(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.frontier(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.frontier(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of();
    savmSpec.requireDeposit = false;
    savmSpec.forceCommitAddresses = List.of();
    savmSpec.initialNonce = 0;
    return savmSpec;
  }

  /**
   * Instantiate Homestead savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec homestead(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.homestead(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.frontier(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of();
    savmSpec.forceCommitAddresses = List.of();
    savmSpec.initialNonce = 0;
    return savmSpec;
  }

  /**
   * Instantiate Tangerine whistle savm savmSpec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec tangerineWhistle(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.tangerineWhistle(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.frontier(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of();
    savmSpec.initialNonce = 0;
    return savmSpec;
  }

  /**
   * Instantiate Spurious dragon savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec spuriousDragon(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.spuriousDragon(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.frontier(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate Byzantium savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec byzantium(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.byzantium(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.byzantium(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate Constantinople savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec constantinople(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.constantinople(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.byzantium(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate Petersburg savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec petersburg(final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.petersburg(savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.byzantium(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate Istanbul savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement =
          "SavmSpec.savmSpec(SavmSpecVersion.ISTANBUL, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec istanbul(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.ISTANBUL, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate Istanbul savm savmSpec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec istanbul(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.istanbul(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.istanbul(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate Berlin savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement = "SavmSpec.savmSpec(SavmSpecVersion.BERLIN, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec berlin(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.BERLIN, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate berlin savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec berlin(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.berlin(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.istanbul(savmSpec.savm.getGasCalculator());
    savmSpec.contractValidationRules = List.of(MaxCodeSizeRule.from(savmSpec.savm));
    return savmSpec;
  }

  /**
   * Instantiate London savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement = "SavmSpec.savmSpec(SavmSpecVersion.LONDON, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec london(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.LONDON, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate London savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec london(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.london(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.istanbul(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaParis savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement = "SavmSpec.savmSpec(SavmSpecVersion.PARIS, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec paris(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.PARIS, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate SilaParis savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec paris(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.paris(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.istanbul(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaShanghai savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement =
          "SavmSpec.savmSpec(SavmSpecVersion.SHANGHAI, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec shanghai(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.SHANGHAI, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate SilaShanghai savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec shanghai(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.shanghai(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.istanbul(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaCancun savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @InlineMe(
      replacement = "SavmSpec.savmSpec(SavmSpecVersion.CANCUN, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec cancun(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.CANCUN, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate SilaCancun savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec cancun(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.cancun(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.cancun(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaPrague savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec prague(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.prague(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.prague(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaOsaka savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec osaka(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.osaka(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.osaka(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate SilaAmsterdam savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec amsterdam(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.amsterdam(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.prague(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate Bogota savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec bogota(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.bogota(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.prague(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate Polis savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec polis(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.polis(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.prague(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate Bangkok savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec bangkok(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.bangkok(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.prague(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate Future SIPs savm spec.
   *
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   * @deprecated Migrate to use {@link SavmSpec#savmSpec(SavmSpecVersion)}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @InlineMe(
      replacement =
          "SavmSpec.savmSpec(SavmSpecVersion.FUTURE_EIPS, BigInteger.ONE, savmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.savm.SavmSpecVersion",
        "org.hyperledger.besu.savm.fluent.SavmSpec"
      })
  @Deprecated(forRemoval = true)
  public static SavmSpec futureEips(final SavmConfiguration savmConfiguration) {
    return savmSpec(SavmSpecVersion.FUTURE_EIPS, BigInteger.ONE, savmConfiguration);
  }

  /**
   * Instantiate Future SIPs savm savmSpec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec futureEips(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec = new SavmSpec(SilaMainnetEVMs.futureEips(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.futureEIPs(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Instantiate Experimental SIPs savm spec.
   *
   * @param chainId the chain ID
   * @param savmConfiguration the savm configuration
   * @return the savm spec
   */
  public static SavmSpec experimentalEips(
      final BigInteger chainId, final SavmConfiguration savmConfiguration) {
    final SavmSpec savmSpec =
        new SavmSpec(SilaMainnetEVMs.experimentalEips(chainId, savmConfiguration));
    savmSpec.precompileContractRegistry =
        SilaMainnetPrecompiledContracts.futureEIPs(savmSpec.savm.getGasCalculator());
    return savmSpec;
  }

  /**
   * Sets Precompile contract registry.
   *
   * @param precompileContractRegistry the precompile contract registry
   * @return the savm spec
   */
  public SavmSpec precompileContractRegistry(
      final PrecompileContractRegistry precompileContractRegistry) {
    this.precompileContractRegistry = precompileContractRegistry;
    return this;
  }

  /**
   * Sets Contract validation rules.
   *
   * @param contractValidationRules the contract validation rules
   * @return the savm spec
   */
  public SavmSpec contractValidationRules(
      final List<ContractValidationRule> contractValidationRules) {
    this.contractValidationRules = contractValidationRules;
    return this;
  }

  /**
   * Sets Initial nonce.
   *
   * @param initialNonce the initial nonce
   * @return the savm spec
   */
  public SavmSpec initialNonce(final long initialNonce) {
    this.initialNonce = initialNonce;
    return this;
  }

  /**
   * Sets Require deposit.
   *
   * @param requireDeposit the require deposit
   * @return the savm spec
   */
  public SavmSpec requireDeposit(final boolean requireDeposit) {
    this.requireDeposit = requireDeposit;
    return this;
  }

  /**
   * List of SIP-718 contracts that require special delete handling. By default, this is only the
   * RIPEMD precompile contract.
   *
   * @param forceCommitAddresses collection of addresses for special handling
   * @return fluent executor
   * @see <a
   *     href="https://github.com/sila/SIPs/issues/716">https://github.com/sila/SIPs/issues/716</a>
   */
  public SavmSpec forceCommitAddresses(final Collection<Address> forceCommitAddresses) {
    this.forceCommitAddresses = forceCommitAddresses;
    return this;
  }

  /**
   * The configured SAVM.
   *
   * @return the SAVM
   */
  public SAVM getEvm() {
    return savm;
  }

  /**
   * Gets the configured precompile contract registry.
   *
   * @return the precompile contract registry
   */
  public PrecompileContractRegistry getPrecompileContractRegistry() {
    return precompileContractRegistry;
  }

  /**
   * Gets the configured contract validation rules.
   *
   * @return the contract validation rules
   */
  public List<ContractValidationRule> getContractValidationRules() {
    return contractValidationRules;
  }

  /**
   * Gets the configured initial nonce.
   *
   * @return the initial nonce
   */
  public long getInitialNonce() {
    return initialNonce;
  }

  /**
   * Checks if deposit required is configured.
   *
   * @return if deposit is required
   */
  public boolean isRequireDeposit() {
    return requireDeposit;
  }

  /**
   * Gets the list of SIP-718 contracts that require special delete handling. By default, this is
   * only the RIPEMD precompile contract.
   *
   * @return collection of addresses for special handling
   * @see <a
   *     href="https://github.com/sila/SIPs/issues/716">https://github.com/sila/SIPs/issues/716</a>
   */
  public Collection<Address> getForceCommitAddresses() {
    return forceCommitAddresses;
  }

  /**
   * Returns the SAVM version
   *
   * @return the current SAVM version
   */
  public SavmSpecVersion getEVMVersion() {
    return savm.getEvmVersion();
  }

  /**
   * Returns the ChainID this savm spec is using
   *
   * @return the current chain ID
   */
  public Optional<Bytes> getChainId() {
    return savm.getChainId();
  }
}
