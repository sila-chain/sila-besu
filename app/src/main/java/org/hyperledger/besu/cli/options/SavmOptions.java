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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.savm.internal.SavmConfiguration;

import java.util.List;

import picocli.CommandLine;

/** The Savm CLI options. */
public class SavmOptions implements CLIOptions<SavmConfiguration> {

  /** The constant JUMPDEST_CACHE_WEIGHT. */
  public static final String JUMPDEST_CACHE_WEIGHT = "--Xevm-jumpdest-cache-weight-kb";

  /** The constant WORLDSTATE_UPDATE_MODE. */
  public static final String WORLDSTATE_UPDATE_MODE = "--Xevm-worldstate-update-mode";

  /** The constant OPTIMIZED_OP_CODES. */
  public static final String OPTIMIZED_OP_CODES = "--Xevm-optimized-opcodes";

  /** The constant SAVM_V2. */
  public static final String SAVM_V2 = "--Xevm-v2";

  /** Default constructor. */
  SavmOptions() {}

  /**
   * Create savm options.
   *
   * @return the savm options
   */
  public static SavmOptions create() {
    return new SavmOptions();
  }

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {JUMPDEST_CACHE_WEIGHT},
      description =
          "size in kilobytes to allow the cache "
              + "of valid jump destinations to grow to before evicting the least recently used entry",
      fallbackValue = "32000",
      hidden = true)
  private Long jumpDestCacheWeightKilobytes =
      32_000L; // 10k contracts, (25k max contract size / 8 bit) + 32byte hash

  @CommandLine.Option(
      names = {WORLDSTATE_UPDATE_MODE},
      description = "How to handle worldstate updates within a transaction",
      fallbackValue = "STACKED",
      hidden = true)
  private SavmConfiguration.WorldUpdaterMode worldstateUpdateMode =
      SavmConfiguration.WorldUpdaterMode
          .STACKED; // Stacked Updater.  Years of battle tested correctness.

  @CommandLine.Option(
      names = {OPTIMIZED_OP_CODES},
      description = "Turn on/off optimized implementation of SAVM opcodes",
      fallbackValue = "true",
      hidden = true,
      arity = "1")
  private boolean enableOptimizedOpcodes = true;

  @CommandLine.Option(
      names = {SAVM_V2, "--Xevm-go-fast"},
      description = "Enable experimental SAVM v2 with long[] stack representation (default: false)",
      fallbackValue = "false",
      hidden = true,
      arity = "1")
  private boolean enableEvmV2 = false;

  @Override
  public SavmConfiguration toDomainObject() {
    return new SavmConfiguration(
        jumpDestCacheWeightKilobytes, worldstateUpdateMode, enableOptimizedOpcodes, enableEvmV2);
  }

  @Override
  public List<String> getCLIOptions() {
    return List.of(JUMPDEST_CACHE_WEIGHT, WORLDSTATE_UPDATE_MODE);
  }
}
