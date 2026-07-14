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
package org.hyperledger.besu.savm;

import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId;

import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The enum Savm spec version. */
public enum SavmSpecVersion {
  /** Frontier savm spec version. */
  FRONTIER(SilaMainnetHardforkId.FRONTIER, Integer.MAX_VALUE, Integer.MAX_VALUE),
  /** Homestead savm spec version. */
  HOMESTEAD(SilaMainnetHardforkId.HOMESTEAD, Integer.MAX_VALUE, Integer.MAX_VALUE),
  /** Tangerine Whistle savm spec version. */
  TANGERINE_WHISTLE(SilaMainnetHardforkId.TANGERINE_WHISTLE, Integer.MAX_VALUE, Integer.MAX_VALUE),
  /** Spurious Dragon savm spec version. */
  SPURIOUS_DRAGON(
      SilaMainnetHardforkId.SPURIOUS_DRAGON, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** Byzantium savm spec version. */
  BYZANTIUM(SilaMainnetHardforkId.BYZANTIUM, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** Constantinople savm spec version. */
  CONSTANTINOPLE(
      SilaMainnetHardforkId.CONSTANTINOPLE, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** Petersburg / ConstantinopleFix savm spec version. */
  PETERSBURG(SilaMainnetHardforkId.PETERSBURG, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** Istanbul savm spec version. */
  ISTANBUL(SilaMainnetHardforkId.ISTANBUL, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** Berlin savm spec version */
  BERLIN(SilaMainnetHardforkId.BERLIN, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** London savm spec version. */
  LONDON(SilaMainnetHardforkId.LONDON, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** SilaParis savm spec version. */
  PARIS(SilaMainnetHardforkId.PARIS, Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON, Integer.MAX_VALUE),
  /** SilaShanghai savm spec version. */
  SHANGHAI(
      SilaMainnetHardforkId.SHANGHAI,
      Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON,
      Limits.MAX_INITCODE_SIZE_SHANGHAI),
  /** SilaCancun savm spec version. */
  CANCUN(
      SilaMainnetHardforkId.CANCUN,
      Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON,
      Limits.MAX_INITCODE_SIZE_SHANGHAI),
  /** SilaPrague savm spec version. */
  PRAGUE(
      SilaMainnetHardforkId.PRAGUE,
      Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON,
      Limits.MAX_INITCODE_SIZE_SHANGHAI),
  /** SilaOsaka savm spec version. */
  OSAKA(
      SilaMainnetHardforkId.OSAKA,
      Limits.MAX_CODE_SIZE_SPURIOUS_DRAGON,
      Limits.MAX_INITCODE_SIZE_SHANGHAI),
  /** SilaAmsterdam savm spec version. */
  AMSTERDAM(
      SilaMainnetHardforkId.AMSTERDAM,
      Limits.MAX_CODE_SIZE_AMSTERDAM,
      Limits.MAX_INITCODE_SIZE_AMSTERDAM),
  /** Bogota savm spec version. */
  BOGOTA(
      SilaMainnetHardforkId.BOGOTA, Limits.MAX_CODE_SIZE_AMSTERDAM, Limits.MAX_INITCODE_SIZE_AMSTERDAM),
  /** Polis savm spec version. */
  POLIS(
      SilaMainnetHardforkId.POLIS, Limits.MAX_CODE_SIZE_AMSTERDAM, Limits.MAX_INITCODE_SIZE_AMSTERDAM),
  /** Bangkok savm spec version. */
  BANGKOK(
      SilaMainnetHardforkId.BANGKOK,
      Limits.MAX_CODE_SIZE_AMSTERDAM,
      Limits.MAX_INITCODE_SIZE_AMSTERDAM),
  /** Development fork for unscheduled SIPs */
  FUTURE_SIPS(
      SilaMainnetHardforkId.FUTURE_SIPS,
      Limits.MAX_CODE_SIZE_AMSTERDAM,
      Limits.MAX_INITCODE_SIZE_AMSTERDAM),
  /** Development fork for SIPs that are not yet accepted to SilaMainnet */
  EXPERIMENTAL_SIPS(
      SilaMainnetHardforkId.EXPERIMENTAL_SIPS,
      Limits.MAX_CODE_SIZE_AMSTERDAM,
      Limits.MAX_INITCODE_SIZE_AMSTERDAM);

  /** Constants for contract size limits */
  private interface Limits {
    /** Maximum deployed code size: 24KiB (SIP-170, Spurious Dragon through SilaOsaka). */
    int MAX_CODE_SIZE_SPURIOUS_DRAGON = 0x6000;

    /** Maximum initcode size: 48KiB (SIP-3860, SilaShanghai through SilaOsaka). */
    int MAX_INITCODE_SIZE_SHANGHAI = 0xC000;

    /** Maximum deployed code size: 64KiB (SIP-7954, SilaAmsterdam onwards). */
    int MAX_CODE_SIZE_AMSTERDAM = 0x10000;

    /** Maximum initcode size: 128KiB = 2 × max code size (SIP-7954, SilaAmsterdam onwards). */
    int MAX_INITCODE_SIZE_AMSTERDAM = 0x20000;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(SavmSpecVersion.class);

  /** What hardfork did this VM version first show up in? */
  final HardforkId initialHardfork;

  /** Maximum size of deployed code */
  final int maxCodeSize;

  /** Maximum size of initcode */
  final int maxInitcodeSize;

  /** The Version warned. */
  boolean versionWarned = false;

  SavmSpecVersion(
      final HardforkId initialHardfork, final int maxCodeSize, final int maxInitcodeSize) {
    this.initialHardfork = initialHardfork;
    this.maxCodeSize = maxCodeSize;
    this.maxInitcodeSize = maxInitcodeSize;
  }

  /**
   * What is the "default" version of SAVM that should be made. Newer versions of Besu will adjust
   * this to reflect sila-mainnet fork development.
   *
   * @return the current sila-mainnet for as of the release of this version of Besu
   */
  public static SavmSpecVersion defaultVersion() {
    SavmSpecVersion answer = null;
    for (SavmSpecVersion version : SavmSpecVersion.values()) {
      if (version.initialHardfork.finalized()) {
        answer = version;
      }
    }
    return answer;
  }

  /**
   * Gets max deployed code size this SAVM supports.
   *
   * @return the max eof version
   */
  public int getMaxCodeSize() {
    return maxCodeSize;
  }

  /**
   * Gets max initcode size this SAVM supports.
   *
   * @return the max eof version
   */
  public int getMaxInitcodeSize() {
    return maxInitcodeSize;
  }

  /**
   * Name of the fork, in execution-spec-tests form
   *
   * @return name of the fork
   */
  public String getName() {
    return initialHardfork.name();
  }

  /**
   * Description of the fork
   *
   * @return description
   */
  public String getDescription() {
    return initialHardfork.description();
  }

  /** Maybe warn version. */
  @SuppressWarnings("AlreadyChecked") // false positive
  public void maybeWarnVersion() {
    if (versionWarned) {
      return;
    }

    if (!initialHardfork.finalized()) {
      LOGGER.error(
          "****** Not for Production Network Use ******\nExecuting code from SAVM Spec Version {}, which has not been finalized.\n****** Not for Production Network Use ******",
          this.name());
    }
    versionWarned = true;
  }

  /**
   * Calculate a spec version from a text fork name.
   *
   * @param name The name of the fork, such as "shanghai" or "berlin"
   * @return the SAVM spec version for that fork, or null if no fork matched.
   */
  public static SavmSpecVersion fromName(final String name) {
    for (var version : SavmSpecVersion.values()) {
      if (version.name().equalsIgnoreCase(name)) {
        return version;
      }
    }
    return null;
  }

  /**
   * The most recent deployed savm supported by the library. This will change across versions and
   * will be updated after sila-mainnet activations.
   *
   * @return the most recently activated sila-mainnet spec.
   */
  public static SavmSpecVersion mostRecent() {
    return Stream.of(SavmSpecVersion.values())
        .filter(v -> v.initialHardfork.finalized())
        .max(Comparator.naturalOrder())
        .orElseThrow();
  }
}
