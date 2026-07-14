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
package org.hyperledger.besu.savm.internal;

import org.hyperledger.besu.savm.frame.MessageFrame;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The type Savm configuration.
 *
 * @param jumpDestCacheWeightKB the jump destination cache weight in kb
 * @param worldUpdaterMode the world updater mode
 * @param enableOptimizedOpcodes enable optimized implementation of certain opcodes in the SAVM
 * @param enableSavmV2 enable experimental SAVM v2 with long[] stack representation
 * @param savmStackSize the maximum savm stack size
 * @param maxCodeSizeOverride An optional override of the maximum code size set by the SAVM fork
 * @param maxInitcodeSizeOverride An optional override of the maximum initcode size set by the SAVM
 *     fork
 */
public record SavmConfiguration(
    long jumpDestCacheWeightKB,
    WorldUpdaterMode worldUpdaterMode,
    boolean enableOptimizedOpcodes,
    boolean enableSavmV2,
    Integer savmStackSize,
    Optional<Integer> maxCodeSizeOverride,
    Optional<Integer> maxInitcodeSizeOverride) {

  /** How should the world state update be handled within transactions? */
  public enum WorldUpdaterMode {
    /**
     * Stack updates, requiring original account and storage values to read through the whole stack
     */
    STACKED,
    /** Share a single state for accounts and storage values, undoing changes on reverts. */
    JOURNALED
  }

  /** The constant DEFAULT. */
  public static final SavmConfiguration DEFAULT =
      new SavmConfiguration(32_000L, WorldUpdaterMode.STACKED, true, false);

  /**
   * Create an SAVM Configuration without any overrides
   *
   * @param jumpDestCacheWeightKilobytes the jump dest cache weight (in kibibytes)
   * @param worldstateUpdateMode the world update mode
   * @param enableOptimizedOpcodes enabled opcode optimizations
   */
  public SavmConfiguration(
      final Long jumpDestCacheWeightKilobytes,
      final WorldUpdaterMode worldstateUpdateMode,
      final boolean enableOptimizedOpcodes) {
    this(
        jumpDestCacheWeightKilobytes,
        worldstateUpdateMode,
        enableOptimizedOpcodes,
        false,
        MessageFrame.DEFAULT_MAX_STACK_SIZE,
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Create an SAVM Configuration without any overrides, with explicit SAVM v2 flag
   *
   * @param jumpDestCacheWeightKilobytes the jump dest cache weight (in kibibytes)
   * @param worldstateUpdateMode the world update mode
   * @param enableOptimizedOpcodes enabled opcode optimizations
   * @param enableSavmV2 enable experimental SAVM v2 with long[] stack representation
   */
  public SavmConfiguration(
      final Long jumpDestCacheWeightKilobytes,
      final WorldUpdaterMode worldstateUpdateMode,
      final boolean enableOptimizedOpcodes,
      final boolean enableSavmV2) {
    this(
        jumpDestCacheWeightKilobytes,
        worldstateUpdateMode,
        enableOptimizedOpcodes,
        enableSavmV2,
        MessageFrame.DEFAULT_MAX_STACK_SIZE,
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Gets jump dest cache weight bytes.
   *
   * @return the jump dest cache weight bytes
   */
  public long getJumpDestCacheWeightBytes() {
    return jumpDestCacheWeightKB * 1024L;
  }

  /**
   * Update the configuration with new overrides, or clearing the overrides with {@link
   * Optional#empty}
   *
   * @param newMaxCodeSize a new max code size override
   * @param newMaxInitcodeSize a new max initcode size override
   * @param newSavmStackSize a new SAVM stack size override
   * @return the updated SAVM configuration
   */
  public SavmConfiguration overrides(
      final OptionalInt newMaxCodeSize,
      final OptionalInt newMaxInitcodeSize,
      final OptionalInt newSavmStackSize) {
    return new SavmConfiguration(
        jumpDestCacheWeightKB,
        worldUpdaterMode,
        enableOptimizedOpcodes,
        enableSavmV2,
        newSavmStackSize.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE),
        newMaxCodeSize.isPresent() ? Optional.of(newMaxCodeSize.getAsInt()) : Optional.empty(),
        newMaxInitcodeSize.isPresent()
            ? Optional.of(newMaxInitcodeSize.getAsInt())
            : Optional.empty());
  }
}
