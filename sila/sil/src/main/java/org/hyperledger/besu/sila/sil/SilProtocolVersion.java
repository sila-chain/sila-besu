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
package org.hyperledger.besu.sila.sil;

import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;

import java.util.Collections;
import java.util.List;

/**
 * Sil protocol messages as defined in <a
 * href="https://github.com/sila/devp2p/blob/master/caps/sil.md">Sila Wire Protocol (SIL)</a>}
 */
public class SilProtocolVersion {
  public static final int V68 = 68;
  public static final int V69 = 69;
  public static final int V70 = 70;
  public static final int V71 = 71;

  /** sil/68 */
  private static final List<Integer> sil68Messages =
      List.of(
          SilProtocolMessages.STATUS,
          SilProtocolMessages.NEW_BLOCK_HASHES,
          SilProtocolMessages.TRANSACTIONS,
          SilProtocolMessages.GET_BLOCK_HEADERS,
          SilProtocolMessages.BLOCK_HEADERS,
          SilProtocolMessages.GET_BLOCK_BODIES,
          SilProtocolMessages.BLOCK_BODIES,
          SilProtocolMessages.NEW_BLOCK,
          SilProtocolMessages.GET_RECEIPTS,
          SilProtocolMessages.RECEIPTS,
          SilProtocolMessages.NEW_POOLED_TRANSACTION_HASHES,
          SilProtocolMessages.GET_POOLED_TRANSACTIONS,
          SilProtocolMessages.POOLED_TRANSACTIONS);

  /**
   * sil/69 SIP-7642
   *
   * <p>Version 69 added the BlockRangeUpdate message.
   */
  private static final List<Integer> sil69Messages =
      List.of(
          SilProtocolMessages.STATUS,
          SilProtocolMessages.NEW_BLOCK_HASHES,
          SilProtocolMessages.TRANSACTIONS,
          SilProtocolMessages.GET_BLOCK_HEADERS,
          SilProtocolMessages.BLOCK_HEADERS,
          SilProtocolMessages.GET_BLOCK_BODIES,
          SilProtocolMessages.BLOCK_BODIES,
          SilProtocolMessages.NEW_BLOCK,
          SilProtocolMessages.GET_RECEIPTS,
          SilProtocolMessages.RECEIPTS,
          SilProtocolMessages.NEW_POOLED_TRANSACTION_HASHES,
          SilProtocolMessages.GET_POOLED_TRANSACTIONS,
          SilProtocolMessages.POOLED_TRANSACTIONS,
          SilProtocolMessages.BLOCK_RANGE_UPDATE);

  /** sil/71 */
  private static final List<Integer> sil71Messages =
      List.of(
          SilProtocolMessages.STATUS,
          SilProtocolMessages.NEW_BLOCK_HASHES,
          SilProtocolMessages.TRANSACTIONS,
          SilProtocolMessages.GET_BLOCK_HEADERS,
          SilProtocolMessages.BLOCK_HEADERS,
          SilProtocolMessages.GET_BLOCK_BODIES,
          SilProtocolMessages.BLOCK_BODIES,
          SilProtocolMessages.NEW_BLOCK,
          SilProtocolMessages.GET_RECEIPTS,
          SilProtocolMessages.RECEIPTS,
          SilProtocolMessages.NEW_POOLED_TRANSACTION_HASHES,
          SilProtocolMessages.GET_POOLED_TRANSACTIONS,
          SilProtocolMessages.POOLED_TRANSACTIONS,
          SilProtocolMessages.BLOCK_RANGE_UPDATE,
          SilProtocolMessages.GET_BLOCK_ACCESS_LISTS,
          SilProtocolMessages.BLOCK_ACCESS_LISTS);

  /**
   * Returns a list of integers containing the supported messages given the protocol version
   *
   * @param protocolVersion the protocol version
   * @return a list containing the codes of supported messages
   */
  public static List<Integer> getSupportedMessages(final int protocolVersion) {
    return switch (protocolVersion) {
      case SilProtocolVersion.V68 -> sil68Messages;
      case SilProtocolVersion.V69, SilProtocolVersion.V70 -> sil69Messages;
      case SilProtocolVersion.V71 -> sil71Messages;
      default -> Collections.emptyList();
    };
  }
}
