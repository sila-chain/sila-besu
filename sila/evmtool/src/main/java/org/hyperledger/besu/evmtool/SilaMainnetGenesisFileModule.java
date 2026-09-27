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
package org.hyperledger.besu.savmtool;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.BlockHeaderFunctions;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetBlockHeaderFunctions;
import org.hyperledger.besu.sila.silaMainnet.SilaMainnetProtocolSchedule;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Named;

import picocli.CommandLine;

class SilaMainnetGenesisFileModule extends GenesisFileModule {

  SilaMainnetGenesisFileModule(final String genesisConfig) {
    super(genesisConfig);
  }

  @Override
  BlockHeaderFunctions blockHashFunction() {
    return new SilaMainnetBlockHeaderFunctions();
  }

  @Override
  ProtocolSchedule provideProtocolSchedule(
      final GenesisConfigOptions configOptions,
      @Named("Fork") final Optional<String> fork,
      @Named("RevertReasonEnabled") final boolean revertReasonEnabled,
      final SavmConfiguration savmConfiguration) {

    configOptions
        .getEcCurve()
        .ifPresent(
            ecCurve -> {
              try {
                SignatureAlgorithmFactory.switchInstance(ecCurve);
              } catch (final IllegalArgumentException e) {
                throw new CommandLine.InitializationException(
                    "Invalid genesis file configuration for ecCurve. " + e.getMessage());
              }
            });

    if (fork.isPresent()) {
      var schedules = createSchedules(configOptions.getChainId().orElse(BigInteger.valueOf(1337)));
      var schedule = schedules.get(fork.get().toLowerCase(Locale.getDefault()));
      if (schedule != null) {
        return schedule.get();
      }
    }

    return SilaMainnetProtocolSchedule.fromConfig(
        configOptions,
        savmConfiguration,
        MiningConfiguration.newDefault(),
        new BadBlockManager(),
        false,
        BalConfiguration.DEFAULT,
        new NoOpMetricsSystem());
  }

  public static Map<String, Supplier<ProtocolSchedule>> createSchedules(final BigInteger chainId) {
    return Map.ofEntries(
        Map.entry("frontier", createSchedule(new StubGenesisConfigOptions().chainId(chainId))),
        Map.entry("homestead", createSchedule(new StubGenesisConfigOptions().homesteadBlock(0))),
        Map.entry("sip150", createSchedule(new StubGenesisConfigOptions().sip150Block(0))),
        Map.entry("sip158", createSchedule(new StubGenesisConfigOptions().sip158Block(0))),
        Map.entry("byzantium", createSchedule(new StubGenesisConfigOptions().byzantiumBlock(0))),
        Map.entry(
            "constantinople",
            createSchedule(new StubGenesisConfigOptions().constantinopleBlock(0))),
        Map.entry(
            "constantinoplefix", createSchedule(new StubGenesisConfigOptions().petersburgBlock(0))),
        Map.entry("petersburg", createSchedule(new StubGenesisConfigOptions().petersburgBlock(0))),
        Map.entry(
            "istanbul",
            createSchedule(new StubGenesisConfigOptions().istanbulBlock(0).chainId(chainId))),
        Map.entry(
            "muirglacier",
            createSchedule(new StubGenesisConfigOptions().muirGlacierBlock(0).chainId(chainId))),
        Map.entry(
            "berlin",
            createSchedule(new StubGenesisConfigOptions().berlinBlock(0).chainId(chainId))),
        Map.entry(
            "london",
            createSchedule(
                new StubGenesisConfigOptions()
                    .londonBlock(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "arrowglacier",
            createSchedule(
                new StubGenesisConfigOptions()
                    .arrowGlacierBlock(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "grayglacier",
            createSchedule(
                new StubGenesisConfigOptions()
                    .grayGlacierBlock(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "merge",
            createSchedule(
                new StubGenesisConfigOptions()
                    .mergeNetSplitBlock(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "shanghai",
            createSchedule(
                new StubGenesisConfigOptions()
                    .shanghaiTime(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "cancun",
            createSchedule(
                new StubGenesisConfigOptions().cancunTime(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "prague",
            createSchedule(
                new StubGenesisConfigOptions().pragueTime(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "osaka",
            createSchedule(
                new StubGenesisConfigOptions().osakaTime(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "bpo1",
            createSchedule(
                new StubGenesisConfigOptions().bpo1Time(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "bpo2",
            createSchedule(
                new StubGenesisConfigOptions().bpo2Time(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "bpo3",
            createSchedule(
                new StubGenesisConfigOptions().bpo3Time(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "bpo4",
            createSchedule(
                new StubGenesisConfigOptions().bpo4Time(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "bpo5",
            createSchedule(
                new StubGenesisConfigOptions().bpo5Time(0).baseFeePerGas(0x0a).chainId(chainId))),
        Map.entry(
            "amsterdam",
            createSchedule(
                new StubGenesisConfigOptions()
                    .amsterdamTime(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "futureeips",
            createSchedule(
                new StubGenesisConfigOptions()
                    .futureEipsTime(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))),
        Map.entry(
            "experimentaleips",
            createSchedule(
                new StubGenesisConfigOptions()
                    .experimentalEipsTime(0)
                    .baseFeePerGas(0x0a)
                    .chainId(chainId))));
  }

  private static Supplier<ProtocolSchedule> createSchedule(final GenesisConfigOptions options) {
    return () ->
        new ProtocolScheduleBuilder(
                options,
                options.getChainId(),
                ProtocolSpecAdapters.create(0, Function.identity()),
                false,
                SavmConfiguration.DEFAULT,
                MiningConfiguration.MINING_DISABLED,
                new BadBlockManager(),
                false,
                BalConfiguration.DEFAULT,
                new NoOpMetricsSystem())
            .createProtocolSchedule();
  }
}
