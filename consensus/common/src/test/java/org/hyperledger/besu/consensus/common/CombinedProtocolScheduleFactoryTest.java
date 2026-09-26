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
package org.hyperledger.besu.consensus.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BERLIN;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BYZANTIUM;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.CONSTANTINOPLE;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.FRONTIER;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.HOMESTEAD;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.LONDON;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.SHANGHAI;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.MilestoneStreamingProtocolSchedule;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.silaMainnet.BalConfiguration;
import org.hyperledger.besu.sila.silaMainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpecAdapters;

import java.math.BigInteger;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CombinedProtocolScheduleFactoryTest {

  private final CombinedProtocolScheduleFactory combinedProtocolScheduleFactory =
      new CombinedProtocolScheduleFactory();

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromSingleProtocolSchedule() {
    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.homesteadBlock(5L);
    genesisConfigOptions.constantinopleBlock(10L);
    genesisConfigOptions.chainId(BigInteger.TEN);
    final BftProtocolSchedule protocolSchedule = createProtocolSchedule(genesisConfigOptions);

    final NavigableSet<ForkSpec<ProtocolSchedule>> consensusSchedule =
        new TreeSet<>(ForkSpec.COMPARATOR);
    consensusSchedule.add(new ForkSpec<>(0, protocolSchedule));

    final BftProtocolSchedule combinedProtocolSchedule =
        combinedProtocolScheduleFactory.create(consensusSchedule, Optional.of(BigInteger.TEN));

    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(0L, 0L).getHardforkId())
        .isEqualTo(FRONTIER);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(0L, 0L))
        .isSameAs(protocolSchedule.getByBlockNumberOrTimestamp(0L, 0L));

    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(5L, 0L).getHardforkId())
        .isEqualTo(HOMESTEAD);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(5L, 0L))
        .isSameAs(protocolSchedule.getByBlockNumberOrTimestamp(5L, 0L));

    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(10L, 0L).getHardforkId())
        .isEqualTo(CONSTANTINOPLE);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(10L, 0L))
        .isSameAs(protocolSchedule.getByBlockNumberOrTimestamp(10L, 0L));

    assertThat(
            new MilestoneStreamingProtocolSchedule(combinedProtocolSchedule)
                .streamMilestoneBlocks()
                .collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L));
  }

  @Test
  public void createsCombinedProtocolScheduleWithMilestonesFromMultipleSchedules() {
    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.homesteadBlock(5L);
    genesisConfigOptions.constantinopleBlock(105L);
    genesisConfigOptions.byzantiumBlock(10L);
    genesisConfigOptions.berlinBlock(110L);
    genesisConfigOptions.londonBlock(220L);
    genesisConfigOptions.shanghaiTime(1000000050L);
    genesisConfigOptions.chainId(BigInteger.TEN);

    final BftProtocolSchedule protocolSchedule1 = createProtocolSchedule(genesisConfigOptions);
    final BftProtocolSchedule protocolSchedule2 = createProtocolSchedule(genesisConfigOptions);
    final BftProtocolSchedule protocolSchedule3 = createProtocolSchedule(genesisConfigOptions);
    final BftProtocolSchedule protocolSchedule4 = createProtocolSchedule(genesisConfigOptions);

    final NavigableSet<ForkSpec<ProtocolSchedule>> consensusSchedule =
        new TreeSet<>(ForkSpec.COMPARATOR);
    consensusSchedule.add(new ForkSpec<>(0, protocolSchedule1));
    consensusSchedule.add(new ForkSpec<>(100L, protocolSchedule2));
    consensusSchedule.add(new ForkSpec<>(200L, protocolSchedule3));
    consensusSchedule.add(new ForkSpec<>(1000000000L, protocolSchedule4));

    final BftProtocolSchedule combinedProtocolSchedule =
        combinedProtocolScheduleFactory.create(consensusSchedule, Optional.of(BigInteger.TEN));

    // consensus schedule 1
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(0L, 0L).getHardforkId())
        .isEqualTo(FRONTIER);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(0L, 0L))
        .isSameAs(protocolSchedule1.getByBlockNumberOrTimestamp(0L, 0L));

    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(5L, 0L).getHardforkId())
        .isEqualTo(HOMESTEAD);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(5L, 0L))
        .isSameAs(protocolSchedule1.getByBlockNumberOrTimestamp(5L, 0L));
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(10L, 0L).getHardforkId())
        .isEqualTo(BYZANTIUM);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(10L, 0L))
        .isSameAs(protocolSchedule1.getByBlockNumberOrTimestamp(10L, 0L));

    // consensus schedule 2 migration block
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(100L, 0L).getHardforkId())
        .isEqualTo(BYZANTIUM);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(100L, 0L))
        .isSameAs(protocolSchedule2.getByBlockNumberOrTimestamp(10L, 0L));

    // consensus schedule 2
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(105L, 0L).getHardforkId())
        .isEqualTo(CONSTANTINOPLE);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(105L, 0L))
        .isSameAs(protocolSchedule2.getByBlockNumberOrTimestamp(105L, 0L));
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(110L, 0L).getHardforkId())
        .isEqualTo(BERLIN);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(110L, 0L))
        .isSameAs(protocolSchedule2.getByBlockNumberOrTimestamp(110L, 0L));

    // consensus schedule 3 migration block
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(200L, 0L).getHardforkId())
        .isEqualTo(BERLIN);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(200L, 0L))
        .isSameAs(protocolSchedule3.getByBlockNumberOrTimestamp(110L, 0L));

    // consensus schedule 3
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(220L, 0L).getHardforkId())
        .isEqualTo(LONDON);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(220L, 0L))
        .isSameAs(protocolSchedule3.getByBlockNumberOrTimestamp(220L, 0L));

    // consensus schedule 4
    assertThat(
            combinedProtocolSchedule.getByBlockNumberOrTimestamp(0L, 1000000050L).getHardforkId())
        .isEqualTo(SHANGHAI);
    assertThat(combinedProtocolSchedule.getByBlockNumberOrTimestamp(220L, 1000000050L))
        .isSameAs(protocolSchedule4.getByBlockNumberOrTimestamp(220L, 1000000050L));

    assertThat(
            new MilestoneStreamingProtocolSchedule(combinedProtocolSchedule)
                .streamMilestoneBlocks()
                .collect(Collectors.toList()))
        .isEqualTo(List.of(0L, 5L, 10L, 100L, 105L, 110L, 200L, 220L, 1000000000L, 1000000050L));
  }

  private BftProtocolSchedule createProtocolSchedule(
      final GenesisConfigOptions genesisConfigOptions) {
    final ProtocolScheduleBuilder protocolScheduleBuilder =
        new ProtocolScheduleBuilder(
            genesisConfigOptions,
            Optional.of(BigInteger.ONE),
            ProtocolSpecAdapters.create(0, Function.identity()),
            false,
            SavmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem());

    return new BftProtocolSchedule(
        (DefaultProtocolSchedule) protocolScheduleBuilder.createProtocolSchedule());
  }
}
