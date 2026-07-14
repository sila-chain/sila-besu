/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.sila.sila-mainnet.milestones;

import static org.hyperledger.besu.sila.sila-mainnet.milestones.MilestoneDefinition.createBlockNumberMilestone;
import static org.hyperledger.besu.sila.sila-mainnet.milestones.MilestoneDefinition.createTimestampMilestone;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetProtocolSpecFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Provides milestone definitions for the Sila SilaMainnet network. */
public class MilestoneDefinitions {

  public static List<MilestoneDefinition> createMilestoneDefinitions(
      final SilaMainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return createSilaMainnetMilestoneDefinitions(specFactory, config);
  }

  /**
   * Creates the milestone definitions for the SilaMainnet network.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of milestone definitions for the SilaMainnet
   */
  private static List<MilestoneDefinition> createSilaMainnetMilestoneDefinitions(
      final SilaMainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    List<MilestoneDefinition> milestones = new ArrayList<>();
    // Add block number milestones first
    milestones.addAll(createSilaMainnetBlockNumberMilestones(specFactory, config));
    // Then add timestamp milestones
    milestones.addAll(createSilaMainnetTimestampMilestones(specFactory, config));
    return milestones;
  }

  /**
   * Creates block number milestones for the SilaMainnet.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of block number milestones
   */
  private static List<MilestoneDefinition> createSilaMainnetBlockNumberMilestones(
      final SilaMainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return List.of(
        createBlockNumberMilestone(
            SilaMainnetHardforkId.FRONTIER, OptionalLong.of(0), specFactory::frontierDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.HOMESTEAD,
            config.getHomesteadBlockNumber(),
            specFactory::homesteadDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.TANGERINE_WHISTLE,
            config.getTangerineWhistleBlockNumber(),
            specFactory::tangerineWhistleDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.SPURIOUS_DRAGON,
            config.getSpuriousDragonBlockNumber(),
            specFactory::spuriousDragonDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.BYZANTIUM,
            config.getByzantiumBlockNumber(),
            specFactory::byzantiumDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.CONSTANTINOPLE,
            config.getConstantinopleBlockNumber(),
            specFactory::constantinopleDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.PETERSBURG,
            config.getPetersburgBlockNumber(),
            specFactory::petersburgDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.ISTANBUL,
            config.getIstanbulBlockNumber(),
            specFactory::istanbulDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.MUIR_GLACIER,
            config.getMuirGlacierBlockNumber(),
            specFactory::muirGlacierDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.BERLIN, config.getBerlinBlockNumber(), specFactory::berlinDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.LONDON, config.getLondonBlockNumber(), specFactory::londonDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.ARROW_GLACIER,
            config.getArrowGlacierBlockNumber(),
            specFactory::arrowGlacierDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.GRAY_GLACIER,
            config.getGrayGlacierBlockNumber(),
            specFactory::grayGlacierDefinition),
        createBlockNumberMilestone(
            SilaMainnetHardforkId.PARIS,
            config.getMergeNetSplitBlockNumber(),
            specFactory::parisDefinition));
  }

  /**
   * Creates timestamp milestones for the SilaMainnet.
   *
   * @param specFactory the protocol spec factory
   * @param config the genesis config options
   * @return a list of timestamp milestones
   */
  private static List<MilestoneDefinition> createSilaMainnetTimestampMilestones(
      final SilaMainnetProtocolSpecFactory specFactory, final GenesisConfigOptions config) {
    return List.of(
        createTimestampMilestone(
            SilaMainnetHardforkId.SHANGHAI, config.getSilaShanghaiTime(), specFactory::shanghaiDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.CANCUN, config.getSilaCancunTime(), specFactory::cancunDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.PRAGUE, config.getSilaPragueTime(), specFactory::pragueDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.OSAKA, config.getSilaOsakaTime(), specFactory::osakaDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.BPO1, config.getBpo1Time(), specFactory::bpo1Definition),
        createTimestampMilestone(
            SilaMainnetHardforkId.BPO2, config.getBpo2Time(), specFactory::bpo2Definition),
        createTimestampMilestone(
            SilaMainnetHardforkId.BPO3, config.getBpo3Time(), specFactory::bpo3Definition),
        createTimestampMilestone(
            SilaMainnetHardforkId.BPO4, config.getBpo4Time(), specFactory::bpo4Definition),
        createTimestampMilestone(
            SilaMainnetHardforkId.BPO5, config.getBpo5Time(), specFactory::bpo5Definition),
        createTimestampMilestone(
            SilaMainnetHardforkId.AMSTERDAM,
            config.getSilaAmsterdamTime(),
            specFactory::amsterdamDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.FUTURE_SIPS,
            config.getFutureSipsTime(),
            specFactory::futureSipsDefinition),
        createTimestampMilestone(
            SilaMainnetHardforkId.EXPERIMENTAL_SIPS,
            config.getExperimentalSipsTime(),
            specFactory::experimentalSipsDefinition));
  }
}
