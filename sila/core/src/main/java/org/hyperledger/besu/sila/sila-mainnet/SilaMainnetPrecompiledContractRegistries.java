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

import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForByzantium;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForSilaCancun;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForFrontier;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForFutureSIPs;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForIstanbul;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForSilaOsaka;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForSilaPrague;

import org.hyperledger.besu.savm.precompile.PrecompileContractRegistry;

/** Provides the various precompiled contracts used on sila-mainnet hard forks. */
public interface SilaMainnetPrecompiledContractRegistries {

  static PrecompileContractRegistry frontier(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFrontier(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry byzantium(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForByzantium(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry istanbul(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForIstanbul(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry cancun(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForSilaCancun(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry prague(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForSilaPrague(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry osaka(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForSilaOsaka(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry futureSips(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFutureSIPs(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }
}
