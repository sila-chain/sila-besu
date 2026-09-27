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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.silstats.util.SilStatsConnectOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;

/** The SilStats CLI options. */
public class SilStatsOptions implements CLIOptions<SilStatsConnectOptions> {

  private static final String SILSTATS = "--silstats";
  private static final String SILSTATS_CONTACT = "--silstats-contact";
  private static final String SILSTATS_CACERT_FILE = "--silstats-cacert-file";
  private static final String SILSTATS_REPORT_INTERVAL = "--silstats-report-interval";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {SILSTATS},
      paramLabel = "<[ws://|wss://]nodename:secret@host:[port]>",
      description = "Reporting URL of a silstats server. Scheme and port can be omitted.")
  private String silStatsUrl = "";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {SILSTATS_CONTACT},
      description = "Contact address to send to silstats server")
  private String silStatsContact = "";

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {SILSTATS_CACERT_FILE},
      paramLabel = "<FILE>",
      description =
          "Specifies the path to the root CA (Certificate Authority) certificate file that has signed silstats server certificate. This option is optional.")
  private Path silStatsCaCert = null;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"})
  @CommandLine.Option(
      names = {SILSTATS_REPORT_INTERVAL},
      paramLabel = "<SECONDS>",
      description = "Interval in seconds between silstats reports.")
  private Integer silStatsReportInterval = 5;

  private SilStatsOptions() {}

  /**
   * Create silstats options.
   *
   * @return the silstats options
   */
  public static SilStatsOptions create() {
    return new SilStatsOptions();
  }

  @Override
  public SilStatsConnectOptions toDomainObject() {
    return SilStatsConnectOptions.fromParams(
        silStatsUrl, silStatsContact, silStatsCaCert, silStatsReportInterval);
  }

  /**
   * Gets silstats url.
   *
   * @return the silstats url
   */
  public String getSilStatsUrl() {
    return silStatsUrl;
  }

  /**
   * Gets silstats contact.
   *
   * @return the silstats contact
   */
  public String getSilStatsContact() {
    return silStatsContact;
  }

  /**
   * Returns path to root CA cert file.
   *
   * @return Path to CA file. null if no CA file to set.
   */
  public Path getSilStatsCaCert() {
    return silStatsCaCert;
  }

  /**
   * Gets silstats report interval.
   *
   * @return the silstats report interval
   */
  public Integer getSilStatsReportInterval() {
    return silStatsReportInterval;
  }

  @Override
  public List<String> getCLIOptions() {
    final List<String> options = new ArrayList<>();
    options.add(SILSTATS + "=" + silStatsUrl);
    options.add(SILSTATS_CONTACT + "=" + silStatsContact);
    if (silStatsCaCert != null) {
      options.add(SILSTATS_CACERT_FILE + "=" + silStatsCaCert);
    }
    options.add(SILSTATS_REPORT_INTERVAL + "=" + silStatsReportInterval);
    return options;
  }
}
