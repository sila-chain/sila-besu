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

import org.hyperledger.besu.config.DiscoveryOptions;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.NetworkDefinition;
import org.hyperledger.besu.sila.p2p.discovery.dns.SilaNodeRecord;
import org.hyperledger.besu.sila.p2p.peers.EnodeURLImpl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * The Sil network config.
 *
 * @param genesisConfig Genesis Config File
 * @param networkId Network Id
 * @param enodeBootNodes Enode Boot Nodes
 * @param enrBootNodes ENR Boot Nodes
 * @param dnsDiscoveryUrl DNS Discovery URL
 */
public record SilNetworkConfig(
    GenesisConfig genesisConfig,
    BigInteger networkId,
    List<EnodeURLImpl> enodeBootNodes,
    List<SilaNodeRecord> enrBootNodes,
    String dnsDiscoveryUrl) {

  /**
   * Validate parameters on new record creation
   *
   * @param genesisConfig the genesis config
   * @param networkId the network id
   * @param enodeBootNodes the Enode boot nodes
   * @param enrBootNodes the ENR boot nodes
   * @param dnsDiscoveryUrl the dns discovery url
   */
  @SuppressWarnings(
      "MethodInputParametersMustBeFinal") // needed since record constructors are not yet supported
  public SilNetworkConfig {
    Objects.requireNonNull(genesisConfig);
    Objects.requireNonNull(enodeBootNodes);
    Objects.requireNonNull(enrBootNodes);
  }

  /**
   * Gets network config.
   *
   * @param networkDefinition the network name
   * @return the network config
   */
  public static SilNetworkConfig getNetworkConfig(final NetworkDefinition networkDefinition) {
    final URL genesisSource = jsonConfigSource(networkDefinition.getGenesisFile());
    final GenesisConfig genesisConfig = GenesisConfig.fromSource(genesisSource);
    final DiscoveryOptions discoveryOptions =
        genesisConfig.getConfigOptions().getDiscoveryOptions();

    final List<EnodeURLImpl> enodeBootNodes =
        discoveryOptions
            .getBootNodes()
            .map(nodes -> nodes.stream().map(EnodeURLImpl::fromString).toList())
            .orElse(List.of());

    final List<SilaNodeRecord> enrBootNodes =
        discoveryOptions
            .getV5BootNodes()
            .map(nodes -> nodes.stream().map(SilaNodeRecord::fromEnr).toList())
            .orElse(List.of());

    return new SilNetworkConfig(
        genesisConfig,
        networkDefinition.getNetworkId(),
        enodeBootNodes,
        enrBootNodes,
        discoveryOptions.getDiscoveryDnsUrl().orElse(null));
  }

  private static URL jsonConfigSource(final String resourceName) {
    return SilNetworkConfig.class.getResource(resourceName);
  }

  /**
   * Json config string.
   *
   * @param network the named network
   * @return the json string
   */
  public static String jsonConfig(final NetworkDefinition network) {
    try (final InputStream genesisFileInputStream =
        SilNetworkConfig.class.getResourceAsStream(network.getGenesisFile())) {
      return new String(genesisFileInputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The type Builder. */
  public static class Builder {

    private String dnsDiscoveryUrl;
    private GenesisConfig genesisConfig;
    private BigInteger networkId;
    private List<EnodeURLImpl> enodeBootNodes;
    private List<SilaNodeRecord> enrBootNodes;

    /**
     * Instantiates a new Builder.
     *
     * @param silNetworkConfig the sil network config
     */
    public Builder(final SilNetworkConfig silNetworkConfig) {
      this.genesisConfig = silNetworkConfig.genesisConfig;
      this.networkId = silNetworkConfig.networkId;
      this.enodeBootNodes = silNetworkConfig.enodeBootNodes;
      this.enrBootNodes = silNetworkConfig.enrBootNodes;
      this.dnsDiscoveryUrl = silNetworkConfig.dnsDiscoveryUrl;
    }

    /**
     * Sets genesis config file.
     *
     * @param genesisConfig the genesis config
     * @return this builder
     */
    public Builder setGenesisConfig(final GenesisConfig genesisConfig) {
      this.genesisConfig = genesisConfig;
      return this;
    }

    /**
     * Sets network id.
     *
     * @param networkId the network id
     * @return this builder
     */
    public Builder setNetworkId(final BigInteger networkId) {
      this.networkId = networkId;
      return this;
    }

    /**
     * Sets boot nodes.
     *
     * @param enodeBootNodes the boot nodes
     * @return this builder
     */
    public Builder setEnodeBootNodes(final List<EnodeURLImpl> enodeBootNodes) {
      this.enodeBootNodes = enodeBootNodes;
      return this;
    }

    /**
     * Sets ENR boot nodes.
     *
     * @param enrBootNodes the boot nodes
     * @return this builder
     */
    public Builder setEnrBootNodes(final List<SilaNodeRecord> enrBootNodes) {
      this.enrBootNodes = enrBootNodes;
      return this;
    }

    /**
     * Sets dns discovery url.
     *
     * @param dnsDiscoveryUrl the dns discovery url
     * @return this builder
     */
    public Builder setDnsDiscoveryUrl(final String dnsDiscoveryUrl) {
      this.dnsDiscoveryUrl = dnsDiscoveryUrl;
      return this;
    }

    /**
     * Build sil network config.
     *
     * @return the sil network config
     */
    public SilNetworkConfig build() {
      return new SilNetworkConfig(
          genesisConfig, networkId, enodeBootNodes, enrBootNodes, dnsDiscoveryUrl);
    }
  }
}
