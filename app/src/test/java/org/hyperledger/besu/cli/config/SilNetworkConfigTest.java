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
package org.hyperledger.besu.cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.config.NetworkDefinition.SILA_MAINNET;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.HOODI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.HOODI_DISCOVERY_URL;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.SILA_MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.SILA_MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_BOOTSTRAP_NODES;
import static org.hyperledger.besu.sila.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_DISCOVERY_URL;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.NetworkDefinition;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SilNetworkConfigTest {

  @Test
  public void testDefaultSilaMainnetConfig() {
    SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(NetworkDefinition.SILA_MAINNET);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(SILA_MAINNET_DISCOVERY_URL);
    assertThat(config.enodeBootNodes()).isEqualTo(SILA_MAINNET_BOOTSTRAP_NODES);
    assertThat(config.enrBootNodes()).isNotEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.ONE);
  }

  @Test
  public void testDefaultSilaSepoliaConfig() {
    SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(NetworkDefinition.SEPOLIA);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(SEPOLIA_DISCOVERY_URL);
    assertThat(config.enodeBootNodes()).isEqualTo(SEPOLIA_BOOTSTRAP_NODES);
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(11155111));
  }

  @Test
  public void testDefaultHoodiConfig() {
    SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(NetworkDefinition.HOODI);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(HOODI_DISCOVERY_URL);
    assertThat(config.enodeBootNodes()).isEqualTo(HOODI_BOOTSTRAP_NODES);
    assertThat(config.enrBootNodes()).isNotEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(560048));
  }

  @Test
  public void testDefaultDevConfig() {
    SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(NetworkDefinition.DEV);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.enodeBootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2018));
  }

  @Test
  public void testDefaultFutureConfig() {
    SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(NetworkDefinition.FUTURE_SIPS);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.enodeBootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2022));
  }

  @Test
  public void testDefaultExperimentalConfig() {
    SilNetworkConfig config =
        SilNetworkConfig.getNetworkConfig(NetworkDefinition.EXPERIMENTAL_SIPS);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.enodeBootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2023));
  }

  @Test
  public void testBuilderWithNetworkId() {
    SilNetworkConfig config =
        new SilNetworkConfig.Builder(SilNetworkConfig.getNetworkConfig(SILA_MAINNET))
            .setNetworkId(BigInteger.valueOf(42))
            .setGenesisConfig(
                GenesisConfig.fromConfig(
                    """
            {
              "config":{
                "chainId":"1234567"
              }
            }
            """))
            .build();
    assertThat(config.genesisConfig().getConfigOptions().getChainId())
        .contains(BigInteger.valueOf(1234567));
    assertThat(config.dnsDiscoveryUrl()).isNotNull();
    assertThat(config.enodeBootNodes()).isNotEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(42));
  }

  @Test
  public void testNetworkDefinitionChainIdsMatchGenesis() {
    for (NetworkDefinition network : NetworkDefinition.values()) {
      SilNetworkConfig config = SilNetworkConfig.getNetworkConfig(network);
      assertThat(config.genesisConfig().getConfigOptions().getChainId().orElseThrow())
          .isEqualTo(network.getChainId());
    }
  }
}
