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
package org.hyperledger.besu.sila.forkid;

import static com.google.common.primitives.Longs.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.sila.forkid.ForkIdTestUtil.mockBlockchain;
import static org.hyperledger.besu.sila.forkid.ForkIdTestUtil.wantPeerCheck;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.forkid.ForkIdTestUtil.ForkIds;
import org.hyperledger.besu.sila.forkid.ForkIdTestUtil.GenesisHash;
import org.hyperledger.besu.sila.forkid.ForkIdTestUtil.Network;
import org.hyperledger.besu.sila.forkid.ForkIdTestUtil.PeerCheckCase;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForkIdTest {
  private static final Logger LOG = LoggerFactory.getLogger(ForkIdTest.class);

  public static Stream<Arguments> data() {
    return Stream.of(
        // SilaMainnet test cases
        Arguments.of(
            "SilaMainnet // Unsynced",
            Network.SILA_MAINNET,
            0L,
            0L,
            ForkIdTestUtil.wantForkId("0xfc64ec04", 1150000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Homestead block",
            Network.SILA_MAINNET,
            1150000L,
            0L,
            ForkIdTestUtil.wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Homestead block",
            Network.SILA_MAINNET,
            1919999L,
            0L,
            ForkIdTestUtil.wantForkId("0x97c2c34c", 1920000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First DAO block",
            Network.SILA_MAINNET,
            1920000L,
            0L,
            ForkIdTestUtil.wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last DAO block",
            Network.SILA_MAINNET,
            2462999L,
            0L,
            ForkIdTestUtil.wantForkId("0x91d1f948", 2463000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Tangerine block",
            Network.SILA_MAINNET,
            2463000L,
            0L,
            ForkIdTestUtil.wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Tangerine block",
            Network.SILA_MAINNET,
            2674999L,
            0L,
            ForkIdTestUtil.wantForkId("0x7a64da13", 2675000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Spurious block",
            Network.SILA_MAINNET,
            2675000L,
            0L,
            ForkIdTestUtil.wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Spurious block",
            Network.SILA_MAINNET,
            4369999L,
            0L,
            ForkIdTestUtil.wantForkId("0x3edd5b10", 4370000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Byzantium block",
            Network.SILA_MAINNET,
            4370000L,
            0L,
            ForkIdTestUtil.wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Byzantium block",
            Network.SILA_MAINNET,
            7279999L,
            0L,
            ForkIdTestUtil.wantForkId("0xa00bc324", 7280000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First and last Constantinople, first Petersburg block",
            Network.SILA_MAINNET,
            7280000L,
            0L,
            ForkIdTestUtil.wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Petersburg block",
            Network.SILA_MAINNET,
            9068999L,
            0L,
            ForkIdTestUtil.wantForkId("0x668db0af", 9069000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Istanbul block",
            Network.SILA_MAINNET,
            9069000L,
            0L,
            ForkIdTestUtil.wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Istanbul block",
            Network.SILA_MAINNET,
            9199999L,
            0L,
            ForkIdTestUtil.wantForkId("0x879d6e30", 9200000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Muir Glacier block",
            Network.SILA_MAINNET,
            9200000L,
            0L,
            ForkIdTestUtil.wantForkId("0xe029e991", 12244000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Muir Glacier block",
            Network.SILA_MAINNET,
            12243999L,
            0L,
            ForkIdTestUtil.wantForkId("0xe029e991", 12244000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Berlin block",
            Network.SILA_MAINNET,
            12244000L,
            0L,
            ForkIdTestUtil.wantForkId("0x0eb440f6", 12965000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last Berlin block",
            Network.SILA_MAINNET,
            12964999L,
            0L,
            ForkIdTestUtil.wantForkId("0x0eb440f6", 12965000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First London block",
            Network.SILA_MAINNET,
            12965000L,
            0L,
            ForkIdTestUtil.wantForkId("0xb715077d", 13773000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Last London block",
            Network.SILA_MAINNET,
            13772999L,
            0L,
            ForkIdTestUtil.wantForkId("0xb715077d", 13773000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Arrow Glacier block",
            Network.SILA_MAINNET,
            13773000L,
            0L,
            ForkIdTestUtil.wantForkId("0x20c327fc", 15050000L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // First Gray Glacier block",
            Network.SILA_MAINNET,
            15050000L,
            0L,
            ForkIdTestUtil.wantForkId("0xf0afd0e3", 0L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        Arguments.of(
            "SilaMainnet // Future Gray Glacier block",
            Network.SILA_MAINNET,
            20000000L,
            0L,
            ForkIdTestUtil.wantForkId("0xf0afd0e3", 0L),
            Optional.of(ForkIds.SILA_MAINNET),
            empty()),
        // Fork ID test cases with block number and timestamp based forks
        // Withdrawals test cases
        Arguments.of(
            "SilaMainnet Withdrawals // First Merge Start block",
            Network.MAINNET_WITH_SHANGHAI,
            18000000L,
            0L,
            ForkIdTestUtil.wantForkId("0x4fb8a872", 1668000000L),
            Optional.of(ForkIdTestUtil.ForkIds.WITHDRAWALS),
            empty()),
        Arguments.of(
            "SilaMainnet Withdrawals // Last Merge Start block",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            0L,
            ForkIdTestUtil.wantForkId("0x4fb8a872", 1668000000L),
            Optional.of(ForkIdTestUtil.ForkIds.WITHDRAWALS),
            empty()),
        Arguments.of(
            "SilaMainnet Withdrawals // First SilaShanghai block",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000000L,
            ForkIdTestUtil.wantForkId("0xc1fdf181", 0L),
            Optional.of(ForkIdTestUtil.ForkIds.WITHDRAWALS),
            empty()),
        Arguments.of(
            "SilaMainnet Withdrawals // Last SilaShanghai block",
            Network.MAINNET_WITH_SHANGHAI,
            20100000L,
            2669000000L,
            ForkIdTestUtil.wantForkId("0xc1fdf181", 0L),
            Optional.of(ForkIdTestUtil.ForkIds.WITHDRAWALS),
            empty()),
        // sepolia
        Arguments.of(
            "SilaSepolia // mergenetsplit block",
            Network.SEPOLIA,
            0L,
            0L,
            ForkIdTestUtil.wantForkId("0xfe3366e7", 1735371L),
            Optional.of(ForkIds.SEPOLIA),
            empty()),
        Arguments.of(
            "SilaSepolia // SilaShanghai",
            Network.SEPOLIA,
            1735371L,
            0L,
            ForkIdTestUtil.wantForkId("0xb96cbd13", 1677557088L),
            Optional.of(ForkIds.SEPOLIA),
            empty()),
        Arguments.of(
            "SilaSepolia // Future",
            Network.SEPOLIA,
            1735372L,
            1677557088L,
            ForkIdTestUtil.wantForkId("0xf7f9bc08", 0L),
            Optional.of(ForkIds.SEPOLIA),
            empty()),
        // Hoodi
        Arguments.of(
            "Hoodi // SilaPrague",
            Network.HOODI,
            0L,
            1742999832L,
            ForkIdTestUtil.wantForkId("0x0929e24e", 0L),
            Optional.of(ForkIds.HOODI),
            empty()),
        // private
        Arguments.of(
            "Private // Unsynced",
            Network.PRIVATE,
            0L,
            0L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
            empty(),
            empty()),
        Arguments.of(
            "Private // First block",
            Network.PRIVATE,
            1L,
            0L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
            empty(),
            empty()),
        Arguments.of(
            "Private // Future block",
            Network.PRIVATE,
            1000000L,
            0L,
            ForkIdTestUtil.wantForkId("0x190a55ad", 0L),
            empty(),
            empty()),
        // peer check
        Arguments.of(
            "check1PetersburgWithRemoteAnnouncingTheSame",
            Network.SILA_MAINNET,
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 0L, true)),
        Arguments.of(
            "check2PetersburgWithRemoteAnnouncingTheSameAndNextFork",
            Network.SILA_MAINNET,
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", Long.MAX_VALUE, true)),
        Arguments.of(
            "check3ByzantiumAwareOfPetersburgRemoteUnawareOfPetersburg",
            Network.SILA_MAINNET,
            7279999L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)),
        Arguments.of(
            "check4ByzantiumAwareOfPetersburgRemoteAwareOfPetersburg",
            Network.SILA_MAINNET,
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7280000L, true)),
        Arguments.of(
            "check5ByzantiumAwareOfPetersburgRemoteAnnouncingUnknownFork",
            Network.SILA_MAINNET,
            7279999L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", Long.MAX_VALUE, true)),
        Arguments.of(
            "check6PetersburgWithRemoteAnnouncingByzantiumAwareOfPetersburg",
            Network.SILA_MAINNET,
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 7280000L, true)),
        Arguments.of(
            "check7PetersburgWithRemoteAnnouncingSpuriousAwareOfByzantiumRemoteMayNeedUpdate",
            Network.SILA_MAINNET,
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x3edd5b10", 4370000L, true)),
        Arguments.of(
            "check8ByzantiumWithRemoteAnnouncingPetersburgLocalOutOfSync",
            Network.SILA_MAINNET,
            727999L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 0L, true)),
        Arguments.of(
            "check9SpuriousWithRemoteAnnouncingByzantiumRemoteUnawareOfPetersburg",
            Network.SILA_MAINNET,
            4369999L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)),
        Arguments.of(
            "check10PetersburgWithRemoteAnnouncingByzantiumRemoteUnawareOfAdditionalForks",
            Network.network(
                GenesisHash.SILA_MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L),
                emptyList()),
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, false)),
        Arguments.of(
            "check11PetersburgWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate",
            Network.network(
                GenesisHash.SILA_MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L),
                emptyList()),
            7987396L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x5cddc0e1", 0L, false)),
        Arguments.of(
            "check12ByzantiumWithRemoteAnnouncingPetersburgAndFutureForkLocalNeedsUpdate",
            ForkIdTestUtil.Network.network(
                GenesisHash.SILA_MAINNET,
                asList(1150000L, 1920000L, 2463000L, 2675000L, 4370000L, 7280000L),
                emptyList()),
            7279999L,
            0L,
            empty(),
            empty(),
            wantPeerCheck("0x5cddc0e1", 0L, false)),
        // Timestamp based peer check cases adapted from SIP-6122 test cases
        Arguments.of(
            "withdrawalsCheck1ShanghaiWithRemoteAnnouncingTheSame",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xc1fdf181", 0L, true)),
        Arguments.of(
            "withdrawalsCheck2ShanghaiWithRemoteAnnouncingSameAndNextFork",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xc1fdf181", Long.MAX_VALUE, true)),
        Arguments.of(
            "withdrawalsCheck3ByzantiumWithRemoteAnnouncingByzantiumNotAwareOfPetersburg",
            Network.MAINNET_WITH_SHANGHAI,
            7279999L,
            1667999999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)),
        Arguments.of(
            "withdrawalsCheck4ByzantiumWithRemoteAnnouncingByzantiumAwareOfPetersburg",
            Network.MAINNET_WITH_SHANGHAI,
            7279999L,
            1667999999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7280000L, true)),
        Arguments.of(
            "withdrawalsCheck5ByzantiumWithRemoteAnnouncingByzantiumAwareOfUnknownFork",
            Network.MAINNET_WITH_SHANGHAI,
            7279999L,
            1667999999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", Long.MAX_VALUE, true)),
        Arguments.of(
            "withdrawalsCheck6ExactlyShanghaiWithRemoteAnnouncingByzantiumAwareOfPetersburgRemoteOutOfSync",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000000L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7280000L, true)),
        Arguments.of(
            "withdrawalsCheck7ShanghaiWithRemoteAnnouncingByzantiumAwareOfPetersburgRemoteOutOfSync",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7280000L, true)),
        Arguments.of(
            "withdrawalsCheck8ShanghaiWithRemoteAnnouncingSpuriousAwareOfByzantium",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0x3edd5b10", 4370000, true)),
        Arguments.of(
            "withdrawalsCheck9ByzantiumWithRemoteAnnouncingPetersburgLocalOutOfSync",
            Network.MAINNET_WITH_SHANGHAI,
            7279999L,
            1667999999L,
            empty(),
            empty(),
            wantPeerCheck("0x668db0af", 4370000, true)),
        Arguments.of(
            "withdrawalsCheck10SpuriousWithRemoteAnnouncingByzantiumNotAwareOfPetersburgLocalOutOfSync",
            Network.MAINNET_WITH_SHANGHAI,
            4369999L,
            1667999999L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, true)),
        Arguments.of(
            "withdrawalsCheck11ShanghaiWithRemoteAnnouncingByzantiumUnawareOfAdditionalForksRemoteNeedsUpdate",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 0L, false)),
        Arguments.of(
            "withdrawalsCheck12ShanghaiAndNotAwareOfAdditionalForksWithRemoteAnnouncingPetersburgAndUnknownFork",
            Network.MAINNET_WITH_SHANGHAI,
            20000000L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0x5cddc0e1", 0L, false)),
        Arguments.of(
            "withdrawalsCheck14ShanghaiWithRemoteAnnouncingUnknownFork",
            Network.MAINNET_WITH_SHANGHAI,
            88888888L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xf0afd0e3", 88888888L, false)),
        Arguments.of(
            "withdrawalsCheck15ShanghaiWithRemoteInByzantiumAnnouncingUnknownFork",
            Network.MAINNET_WITH_SHANGHAI,
            88888888L,
            1668000001L,
            empty(),
            empty(),
            wantPeerCheck("0xa00bc324", 7279999L, false)));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("data")
  public void test(
      final String name,
      final ForkIdTestUtil.Network network,
      final long head,
      final long time,
      final Optional<ForkId> wantForkId,
      final Optional<List<ForkId>> wantForkIds,
      final Optional<PeerCheckCase> wantPeerCheckCase) {
    LOG.info("Running test case {}", name);
    final ForkIdManager forkIdManager =
        new ForkIdManager(
            mockBlockchain(network.hash, head, time), network.blockForks, network.timestampForks);
    wantForkId.ifPresent(
        forkId -> assertThat(forkIdManager.getForkIdForChainHead()).isEqualTo(forkId));
    wantForkIds.ifPresent(
        forkIds -> assertThat(forkIdManager.getAllForkIds()).containsExactlyElementsOf(forkIds));
    wantPeerCheckCase.ifPresent(
        peerCheckCase ->
            assertThat(
                    forkIdManager.peerCheck(
                        new ForkId(
                            Bytes.fromHexString(peerCheckCase.forkIdHash),
                            peerCheckCase.forkIdNext)))
                .isEqualTo(peerCheckCase.want));
  }

  @Test
  public void testGenesisTimestampEqualToShanghaiTimestamp() {
    final ForkIdManager forkIdManager =
        new ForkIdManager(
            mockBlockchain(Hash.ZERO.getBytes().toHexString(), 10L, 0L),
            Collections.emptyList(),
            List.of(1L, 2L));
    assertThat(forkIdManager.getAllForkIds().size()).isEqualTo(2);
    assertThat(forkIdManager.getAllForkIds().get(0).getNext()).isEqualTo(2L);
    assertThat(forkIdManager.getAllForkIds().get(1).getNext()).isEqualTo(0L);
  }

  @Test
  public void testNoBlockNoForksAndNoTimestampForksGreaterThanGenesisTimestamp() {
    final ForkIdManager forkIdManager =
        new ForkIdManager(
            mockBlockchain(Hash.ZERO.getBytes().toHexString(), 10L, 1L),
            Collections.emptyList(),
            List.of(1L));
    assertThat(forkIdManager.getAllForkIds().size()).isEqualTo(0);
    // There is no ForkId, so peerCheck always has to return true
    assertThat(forkIdManager.peerCheck(new ForkId(Bytes.fromHexString("0xdeadbeef"), 100L)))
        .isEqualTo(true);
  }
}
