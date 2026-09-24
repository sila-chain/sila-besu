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
package org.hyperledger.besu.sila.referencetests;

import org.hyperledger.besu.config.BlobScheduleOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpec;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecAdapters;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Loads all available protocol schedules into memory and lets users select the appropriate one.
 * Beware of the high memory usage and object allocation cost since **all** protocol schedules will
 * be created and initialized for each created instance. This might cause your tests to slow down.
 */
public class ReferenceTestProtocolSchedules {

  private static final BigInteger CHAIN_ID = BigInteger.ONE;

  private static final List<String> SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS =
      Arrays.asList("Frontier", "Homestead", "SIP150");

  private static final Set<String> FORKS_WITHOUT_BLOCK_BUILDING =
      Set.of(
          "frontier",
          "frontiertohomesteadat5",
          "homestead",
          "homesteadtoeip150at5",
          "homesteadtodaoat5",
          "sip150",
          "sip158",
          "sip158tobyzantiumat5");

  /** Guarded by {@link #cached(SavmConfiguration, BlobScheduleOptions)}, which is synchronized. */
  private static final Map<CacheKey, ReferenceTestProtocolSchedules> CACHED_SCHEDULES =
      new HashMap<>();

  private record CacheKey(SavmConfiguration savmConfiguration, ObjectNode blobSchedule) {}

  public static ReferenceTestProtocolSchedules create() {
    return create(new StubGenesisConfigOptions(), SavmConfiguration.DEFAULT);
  }

  public static ReferenceTestProtocolSchedules create(final SavmConfiguration savmConfiguration) {
    return create(new StubGenesisConfigOptions(), savmConfiguration);
  }

  /**
   * Creates the reference-test schedules with a fixture-supplied blob schedule applied to every
   * fork (used by engine/blockchain tests on devnets whose blob target/max differ from defaults).
   *
   * @param savmConfiguration the SAVM configuration
   * @param blobScheduleOptions the blob schedule from the fixture config, or null for defaults
   * @return the schedules
   */
  public static ReferenceTestProtocolSchedules create(
      final SavmConfiguration savmConfiguration, final BlobScheduleOptions blobScheduleOptions) {
    final StubGenesisConfigOptions genesisStub = new StubGenesisConfigOptions();
    if (blobScheduleOptions != null) {
      genesisStub.blobScheduleOptions(blobScheduleOptions);
    }
    return create(genesisStub, savmConfiguration);
  }

  /**
   * As {@link #create(SavmConfiguration, BlobScheduleOptions)}, but built once per distinct blob
   * schedule and shared by every caller. Fixture runners hand each fixture's own blob schedule in,
   * and a fixture tree holds only a handful of distinct ones, so building a schedule set per
   * fixture is pure waste.
   *
   * <p>Synchronized rather than a {@code ConcurrentHashMap.computeIfAbsent}: the map holds a key
   * per distinct blob schedule, so per-key serialisation would still let several threads into
   * {@code create()} at once, and {@code create()} initialises the KZG trusted setup, which is
   * process-wide state guarded by a {@code compareAndSet} that lets the losing thread proceed
   * before the setup is loaded. The check-then-act has to be atomic across keys, not merely
   * visible.
   *
   * <p>The key is the blob schedule's whole config root rather than {@link
   * BlobScheduleOptions#asMap()}, which enumerates only the fork keys this Besu version has a
   * getter for and would silently collide two schedules differing only outside that set — the
   * devnet forks these runners exist to test. {@link
   * com.fasterxml.jackson.databind.node.ObjectNode} equality is by content and insensitive to field
   * order.
   *
   * @param savmConfiguration the SAVM configuration
   * @param blobScheduleOptions the blob schedule from the fixture config, or null for defaults
   * @return the schedules
   */
  public static synchronized ReferenceTestProtocolSchedules cached(
      final SavmConfiguration savmConfiguration, final BlobScheduleOptions blobScheduleOptions) {
    final ObjectNode key =
        blobScheduleOptions == null
            ? JsonUtil.createEmptyObjectNode()
            : blobScheduleOptions.getConfigRoot().deepCopy();
    return CACHED_SCHEDULES.computeIfAbsent(
        new CacheKey(savmConfiguration, key),
        ignored -> create(savmConfiguration, blobScheduleOptions));
  }

  public static ReferenceTestProtocolSchedules create(
      final StubGenesisConfigOptions genesisStub, final SavmConfiguration savmConfiguration) {
    // the following schedules activate SIP-1559, but may have non-default
    if (genesisStub.getBaseFeePerGas().isEmpty()) {
      genesisStub.baseFeePerGas(0x0a);
    }
    // also load KZG file for mainnet
    KZGPointEvalPrecompiledContract.init();
    return new ReferenceTestProtocolSchedules(
        Map.ofEntries(
                Map.entry("Frontier", createSchedule(genesisStub.clone(), savmConfiguration)),
                Map.entry(
                    "FrontierToHomesteadAt5",
                    createSchedule(genesisStub.clone().homesteadBlock(5), savmConfiguration)),
                Map.entry(
                    "Homestead",
                    createSchedule(genesisStub.clone().homesteadBlock(0), savmConfiguration)),
                Map.entry(
                    "HomesteadToEIP150At5",
                    createSchedule(
                        genesisStub.clone().homesteadBlock(0).sip150Block(5), savmConfiguration)),
                Map.entry(
                    "HomesteadToDaoAt5",
                    createSchedule(
                        genesisStub.clone().homesteadBlock(0).daoForkBlock(5), savmConfiguration)),
                Map.entry(
                    "SIP150",
                    createSchedule(genesisStub.clone().sip150Block(0), savmConfiguration)),
                Map.entry(
                    "SIP158",
                    createSchedule(genesisStub.clone().sip158Block(0), savmConfiguration)),
                Map.entry(
                    "SIP158ToByzantiumAt5",
                    createSchedule(
                        genesisStub.clone().sip158Block(0).byzantiumBlock(5), savmConfiguration)),
                Map.entry(
                    "Byzantium",
                    createSchedule(genesisStub.clone().byzantiumBlock(0), savmConfiguration)),
                Map.entry(
                    "Constantinople",
                    createSchedule(genesisStub.clone().constantinopleBlock(0), savmConfiguration)),
                Map.entry(
                    "ConstantinopleFix",
                    createSchedule(genesisStub.clone().petersburgBlock(0), savmConfiguration)),
                Map.entry(
                    "Petersburg",
                    createSchedule(genesisStub.clone().petersburgBlock(0), savmConfiguration)),
                Map.entry(
                    "Istanbul",
                    createSchedule(genesisStub.clone().istanbulBlock(0), savmConfiguration)),
                Map.entry(
                    "MuirGlacier",
                    createSchedule(genesisStub.clone().muirGlacierBlock(0), savmConfiguration)),
                Map.entry(
                    "Berlin",
                    createSchedule(genesisStub.clone().berlinBlock(0), savmConfiguration)),
                Map.entry(
                    "London",
                    createSchedule(genesisStub.clone().londonBlock(0), savmConfiguration)),
                Map.entry(
                    "ArrowGlacier",
                    createSchedule(genesisStub.clone().arrowGlacierBlock(0), savmConfiguration)),
                Map.entry(
                    "GrayGlacier",
                    createSchedule(genesisStub.clone().grayGlacierBlock(0), savmConfiguration)),
                Map.entry(
                    "Merge",
                    createSchedule(genesisStub.clone().mergeNetSplitBlock(0), savmConfiguration)),
                Map.entry(
                    "SilaParis",
                    createSchedule(genesisStub.clone().mergeNetSplitBlock(0), savmConfiguration)),
                Map.entry(
                    "SilaParisToShanghaiAtTime15k",
                    createSchedule(
                        genesisStub.clone().mergeNetSplitBlock(0).shanghaiTime(15000),
                        savmConfiguration)),
                Map.entry(
                    "SilaShanghai",
                    createSchedule(genesisStub.clone().shanghaiTime(0), savmConfiguration)),
                Map.entry(
                    "SilaShanghaiToCancunAtTime15k",
                    createSchedule(
                        genesisStub.clone().shanghaiTime(0).cancunTime(15000), savmConfiguration)),
                Map.entry(
                    "SilaCancun",
                    createSchedule(genesisStub.clone().cancunTime(0), savmConfiguration)),
                Map.entry(
                    "SilaCancunToPragueAtTime15k",
                    createSchedule(
                        genesisStub.clone().cancunTime(0).pragueTime(15000), savmConfiguration)),
                Map.entry(
                    "SilaPrague",
                    createSchedule(genesisStub.clone().pragueTime(0), savmConfiguration)),
                // Forks left without an activation time are folded into the first configured
                // milestone, so each entry only needs to give times to the forks that have to be
                // told apart (the fork under test and, for transitions, the one it starts from).
                Map.entry(
                    "SilaPragueToOsakaAtTime15k",
                    createSchedule(
                        genesisStub.clone().pragueTime(0).osakaTime(15000), savmConfiguration)),
                Map.entry(
                    "SilaOsaka",
                    createSchedule(
                        genesisStub.clone().pragueTime(0).osakaTime(0), savmConfiguration)),
                Map.entry(
                    "SilaOsakaToBPO1AtTime15k",
                    createSchedule(
                        genesisStub.clone().pragueTime(0).osakaTime(0).bpo1Time(15000),
                        savmConfiguration)),
                Map.entry(
                    "BPO1ToBPO2AtTime15k",
                    createSchedule(
                        genesisStub.clone().pragueTime(0).osakaTime(0).bpo1Time(0).bpo2Time(15000),
                        savmConfiguration)),
                Map.entry(
                    "BPO2ToBPO3AtTime15k",
                    createSchedule(
                        genesisStub
                            .clone()
                            .pragueTime(0)
                            .osakaTime(0)
                            .bpo1Time(0)
                            .bpo2Time(0)
                            .bpo3Time(15000),
                        savmConfiguration)),
                Map.entry(
                    "BPO3ToBPO4AtTime15k",
                    createSchedule(
                        genesisStub
                            .clone()
                            .pragueTime(0)
                            .osakaTime(0)
                            .bpo1Time(0)
                            .bpo2Time(0)
                            .bpo3Time(0)
                            .bpo4Time(15000),
                        savmConfiguration)),
                Map.entry(
                    "SilaAmsterdam",
                    createSchedule(
                        genesisStub
                            .clone()
                            .pragueTime(0)
                            .osakaTime(0)
                            .bpo1Time(0)
                            .bpo2Time(0)
                            .amsterdamTime(0),
                        savmConfiguration)),
                Map.entry(
                    "BPO2ToAmsterdamAtTime15k",
                    createSchedule(
                        genesisStub
                            .clone()
                            .pragueTime(0)
                            .osakaTime(0)
                            .bpo1Time(0)
                            .bpo2Time(0)
                            .amsterdamTime(15000),
                        savmConfiguration)),
                Map.entry(
                    "Bogota",
                    createSchedule(genesisStub.clone().futureEipsTime(0), savmConfiguration)),
                Map.entry(
                    "Polis",
                    createSchedule(genesisStub.clone().futureEipsTime(0), savmConfiguration)),
                Map.entry(
                    "Bangkok",
                    createSchedule(genesisStub.clone().futureEipsTime(0), savmConfiguration)),
                Map.entry(
                    "Future_EIPs",
                    createSchedule(genesisStub.clone().futureEipsTime(0), savmConfiguration)),
                Map.entry(
                    "Experimental_EIPs",
                    createSchedule(genesisStub.clone().experimentalEipsTime(0), savmConfiguration)))
            .entrySet()
            .stream()
            .map(e -> Map.entry(e.getKey().toLowerCase(Locale.ROOT), e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private final Map<String, ProtocolSchedule> schedules;

  private ReferenceTestProtocolSchedules(final Map<String, ProtocolSchedule> schedules) {
    this.schedules = schedules;
  }

  public ProtocolSchedule getByName(final String name) {
    return schedules.get(name.toLowerCase(Locale.ROOT));
  }

  public ProtocolSpec geSpecByName(final String name) {
    ProtocolSchedule schedule = getByName(name);
    if (schedule == null) {
      return null;
    }
    BlockHeader header =
        new BlockHeaderTestFixture().timestamp(Long.MAX_VALUE).number(Long.MAX_VALUE).buildHeader();
    return schedule.getByBlockHeader(header);
  }

  private static ProtocolSchedule createSchedule(
      final GenesisConfigOptions options, final SavmConfiguration savmConfiguration) {
    return new ProtocolScheduleBuilder(
            options,
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(0, Function.identity()),
            false,
            savmConfiguration,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem())
        .createProtocolSchedule();
  }

  public static boolean shouldClearEmptyAccounts(final String fork) {
    return !SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS.contains(fork);
  }

  public static boolean supportsBlockBuilding(final String fork) {
    return !FORKS_WITHOUT_BLOCK_BUILDING.contains(fork.toLowerCase(Locale.ROOT));
  }
}
