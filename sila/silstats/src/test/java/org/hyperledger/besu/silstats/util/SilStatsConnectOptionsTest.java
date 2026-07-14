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
package org.hyperledger.besu.silstats.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SilStatsConnectOptionsTest {

  private static final Path TEST_CA_CERT = Path.of("./unused-ca-cert.pem");

  private final String VALID_NETSTATS_URL = "Dev-Node-1:secret-with-dashes@127.0.0.1:3001";

  private final String CONTACT = "contact@mail.fr";

  private final String ERROR_MESSAGE =
      "Invalid silstats URL syntax. Silstats URL should have the following format '[ws://|wss://]nodename:secret@host[:port]'.";

  @Test
  public void buildWithValidParams() {
    final Path caCert = Path.of("./test.pem");
    final SilStatsConnectOptions silStatsConnectOptions =
        SilStatsConnectOptions.fromParams(VALID_NETSTATS_URL, CONTACT, caCert);
    assertThat(silStatsConnectOptions.getScheme()).isNull();
    assertThat(silStatsConnectOptions.getHost()).isEqualTo("127.0.0.1");
    assertThat(silStatsConnectOptions.getNodeName()).isEqualTo("Dev-Node-1");
    assertThat(silStatsConnectOptions.getPort()).isEqualTo(3001);
    assertThat(silStatsConnectOptions.getSecret()).isEqualTo("secret-with-dashes");
    assertThat(silStatsConnectOptions.getContact()).isEqualTo(CONTACT);
    assertThat(silStatsConnectOptions.getCaCert()).isEqualTo(caCert);
  }

  @ParameterizedTest(name = "#{index} - With Host {0}")
  @ValueSource(strings = {"url-test.test.com", "url.test.com", "test.com", "10.10.10.15"})
  public void buildWithValidHost(final String host) {
    final SilStatsConnectOptions silStatsConnectOptions =
        SilStatsConnectOptions.fromParams(
            "Dev-Node-1:secret@" + host + ":3001", CONTACT, TEST_CA_CERT);
    assertThat(silStatsConnectOptions.getScheme()).isNull();
    assertThat(silStatsConnectOptions.getHost()).isEqualTo(host);
    assertThat(silStatsConnectOptions.getPort()).isEqualTo(3001);
  }

  @ParameterizedTest(name = "#{index} - With Host {0}")
  @ValueSource(strings = {"url-test.test.com", "url.test.com", "test.com", "10.10.10.15"})
  public void buildWithValidHostWithoutPort(final String host) {
    final SilStatsConnectOptions silStatsConnectOptions =
        SilStatsConnectOptions.fromParams("Dev-Node-1:secret@" + host, CONTACT, TEST_CA_CERT);
    assertThat(silStatsConnectOptions.getScheme()).isNull();
    assertThat(silStatsConnectOptions.getHost()).isEqualTo(host);
    assertThat(silStatsConnectOptions.getPort()).isEqualTo(-1);
  }

  @ParameterizedTest(name = "#{index} - With Scheme {0}")
  @ValueSource(strings = {"ws", "wss", "WSS", "WS"})
  public void buildWithValidScheme(final String scheme) {
    final SilStatsConnectOptions silStatsConnectOptions =
        SilStatsConnectOptions.fromParams(
            scheme + "://Dev-Node-1:secret@url-test.test.com:3001", CONTACT, TEST_CA_CERT);
    assertThat(silStatsConnectOptions.getScheme()).isEqualTo(scheme);
    assertThat(silStatsConnectOptions.getHost()).isEqualTo("url-test.test.com");
    assertThat(silStatsConnectOptions.getPort()).isEqualTo(3001);
  }

  @ParameterizedTest(name = "#{index} - With Scheme {0}")
  @ValueSource(strings = {"http", "https", "ftp"})
  public void shouldRaiseErrorOnInvalidScheme(final String scheme) {
    // missing node name
    assertThatThrownBy(
            () ->
                SilStatsConnectOptions.fromParams(
                    scheme + "://Dev-Node-1:secret@url-test.test.com:3001", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }

  @Test
  public void shouldDetectEmptyParams() {
    assertThatThrownBy(() -> SilStatsConnectOptions.fromParams("", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }

  @Test
  public void shouldDetectMissingParams() {
    // missing node name
    assertThatThrownBy(
            () -> SilStatsConnectOptions.fromParams("secret@127.0.0.1:3001", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // missing host
    assertThatThrownBy(
            () -> SilStatsConnectOptions.fromParams("Dev-Node-1:secret@", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // missing port in URL should default to -1
    SilStatsConnectOptions silStatsConnectOptions =
        SilStatsConnectOptions.fromParams("Dev-Node-1:secret@127.0.0.1:", CONTACT, TEST_CA_CERT);
    assertThat(silStatsConnectOptions.getPort()).isEqualTo(-1);
  }

  @Test
  public void shouldDetectInvalidParams() {
    // invalid host
    assertThatThrownBy(
            () ->
                SilStatsConnectOptions.fromParams(
                    "Dev-Node-1:secret@127.0@0.1:3001", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // invalid port
    assertThatThrownBy(
            () ->
                SilStatsConnectOptions.fromParams(
                    "Dev-Node-1:secret@127.0.0.1:A001", CONTACT, TEST_CA_CERT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }
}
