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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.BalConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpec;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
          "homesteadtosip150at5",
          "homesteadtodaoat5",
          "sip150",
          "sip158",
          "sip158tobyzantiumat5");

  public static ReferenceTestProtocolSchedules create() {
    return create(new StubGenesisConfigOptions(), SavmConfiguration.DEFAULT);
  }

  public static ReferenceTestProtocolSchedules create(final SavmConfiguration savmConfiguration) {
    return create(new StubGenesisConfigOptions(), savmConfiguration);
  }

  public static ReferenceTestProtocolSchedules create(
      final StubGenesisConfigOptions genesisStub, final SavmConfiguration savmConfiguration) {
    // the following schedules activate SIP-1559, but may have non-default
    if (genesisStub.getBaseFeePerGas().isEmpty()) {
      genesisStub.baseFeePerGas(0x0a);
    }
    // also load KZG file for sila-mainnet
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
                    "HomesteadToSIP150At5",
                    createSchedule(
                        genesisStub.clone().homesteadBlock(0).sip150Block(5), savmConfiguration)),
                Map.entry(
                    "HomesteadToDaoAt5",
                    createSchedule(
                        genesisStub.clone().homesteadBlock(0).daoForkBlock(5), savmConfiguration)),
                Map.entry(
                    "SIP150", createSchedule(genesisStub.clone().sip150Block(0), savmConfiguration)),
                Map.entry(
                    "SIP158", createSchedule(genesisStub.clone().sip158Block(0), savmConfiguration)),
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
                    "Berlin", createSchedule(genesisStub.clone().berlinBlock(0), savmConfiguration)),
                Map.entry(
                    "London", createSchedule(genesisStub.clone().londonBlock(0), savmConfiguration)),
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
                    "SilaShanghai",
                    createSchedule(genesisStub.clone().shanghaiTime(0), savmConfiguration)),
                Map.entry(
                    "SilaShanghaiToSilaCancunAtTime15k",
                    createSchedule(
                        genesisStub.clone().shanghaiTime(0).cancunTime(15000), savmConfiguration)),
                Map.entry(
                    "SilaCancun", createSchedule(genesisStub.clone().cancunTime(0), savmConfiguration)),
                Map.entry(
                    "SilaCancunToSilaPragueAtTime15k",
                    createSchedule(
                        genesisStub.clone().cancunTime(0).pragueTime(15000), savmConfiguration)),
                Map.entry(
                    "SilaPrague", createSchedule(genesisStub.clone().pragueTime(0), savmConfiguration)),
                Map.entry(
                    "SilaOsaka", createSchedule(genesisStub.clone().osakaTime(0), savmConfiguration)),
                Map.entry(
                    "SilaAmsterdam",
                    createSchedule(genesisStub.clone().amsterdamTime(0), savmConfiguration)),
                Map.entry(
                    "Bogota",
                    createSchedule(genesisStub.clone().futureSipsTime(0), savmConfiguration)),
                Map.entry(
                    "Polis",
                    createSchedule(genesisStub.clone().futureSipsTime(0), savmConfiguration)),
                Map.entry(
                    "Bangkok",
                    createSchedule(genesisStub.clone().futureSipsTime(0), savmConfiguration)),
                Map.entry(
                    "Future_SIPs",
                    createSchedule(genesisStub.clone().futureSipsTime(0), savmConfiguration)),
                Map.entry(
                    "Experimental_SIPs",
                    createSchedule(genesisStub.clone().experimentalSipsTime(0), savmConfiguration)))
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
