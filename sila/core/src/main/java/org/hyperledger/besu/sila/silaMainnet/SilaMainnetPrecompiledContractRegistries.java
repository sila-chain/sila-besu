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
package org.hyperledger.besu.sila.silaMainnet;

import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForByzantium;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForCancun;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForFrontier;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForFutureEIPs;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForIstanbul;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForOsaka;
import static org.hyperledger.besu.savm.precompile.SilaMainnetPrecompiledContracts.populateForPrague;

import org.hyperledger.besu.savm.precompile.PrecompileContractRegistry;

/** Provides the various precompiled contracts used on mainnet hard forks. */
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
    populateForCancun(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry prague(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForPrague(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry osaka(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForOsaka(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }

  static PrecompileContractRegistry futureEips(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry = new PrecompileContractRegistry();
    populateForFutureEIPs(registry, precompiledContractConfiguration.getGasCalculator());
    return registry;
  }
}
