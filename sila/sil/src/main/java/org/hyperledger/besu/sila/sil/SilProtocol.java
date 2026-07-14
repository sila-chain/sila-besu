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
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.SubProtocol;

import java.util.BitSet;
import java.util.Set;

/**
 * Sil protocol messages as defined in <a
 * href="https://github.com/sila-chain/devp2p/blob/master/caps/sil.md">Sila Wire Protocol
 * (SIL)</a>
 */
public class SilProtocol implements SubProtocol {
  public static final String NAME = "sil";
  private static final SilProtocol INSTANCE = new SilProtocol();
  public static final Capability SIL68 = Capability.create(NAME, SilProtocolVersion.V68);
  public static final Capability SIL69 = Capability.create(NAME, SilProtocolVersion.V69);
  public static final Capability SIL70 = Capability.create(NAME, SilProtocolVersion.V70);
  public static final Capability SIL71 = Capability.create(NAME, SilProtocolVersion.V71);
  public static final BitSet REQUEST_ID_MESSAGES;

  static {
    final var requestIdMessages =
        Set.of(
            SilProtocolMessages.GET_BLOCK_HEADERS,
            SilProtocolMessages.BLOCK_HEADERS,
            SilProtocolMessages.GET_BLOCK_BODIES,
            SilProtocolMessages.BLOCK_BODIES,
            SilProtocolMessages.GET_POOLED_TRANSACTIONS,
            SilProtocolMessages.POOLED_TRANSACTIONS,
            SilProtocolMessages.GET_RECEIPTS,
            SilProtocolMessages.RECEIPTS,
            SilProtocolMessages.GET_BLOCK_ACCESS_LISTS,
            SilProtocolMessages.BLOCK_ACCESS_LISTS);
    REQUEST_ID_MESSAGES =
        new BitSet(requestIdMessages.stream().mapToInt(i -> i).max().getAsInt() + 1);
    requestIdMessages.forEach(REQUEST_ID_MESSAGES::set);
  }

  // Latest version of the Sil protocol
  public static final Capability LATEST = SIL71;

  public static boolean requestIdCompatible(final int code) {
    return REQUEST_ID_MESSAGES.get(code);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int messageSpace(final int protocolVersion) {
    return switch (protocolVersion) {
      case SilProtocolVersion.V68 -> 17;
      case SilProtocolVersion.V69, SilProtocolVersion.V70 -> 18;
      case SilProtocolVersion.V71 -> 20;
      default -> 0;
    };
  }

  @Override
  public boolean isValidMessageCode(final int protocolVersion, final int code) {
    return SilProtocolVersion.getSupportedMessages(protocolVersion).contains(code);
  }

  @Override
  public String messageName(final int protocolVersion, final int code) {
    return switch (code) {
      case SilProtocolMessages.STATUS -> "Status";
      case SilProtocolMessages.NEW_BLOCK_HASHES -> "NewBlockHashes";
      case SilProtocolMessages.TRANSACTIONS -> "Transactions";
      case SilProtocolMessages.GET_BLOCK_HEADERS -> "GetBlockHeaders";
      case SilProtocolMessages.BLOCK_HEADERS -> "BlockHeaders";
      case SilProtocolMessages.GET_BLOCK_BODIES -> "GetBlockBodies";
      case SilProtocolMessages.BLOCK_BODIES -> "BlockBodies";
      case SilProtocolMessages.NEW_BLOCK -> "NewBlock";
      case SilProtocolMessages.NEW_POOLED_TRANSACTION_HASHES -> "NewPooledTransactionHashes";
      case SilProtocolMessages.GET_POOLED_TRANSACTIONS -> "GetPooledTransactions";
      case SilProtocolMessages.POOLED_TRANSACTIONS -> "PooledTransactions";
      case SilProtocolMessages.GET_RECEIPTS -> "GetReceipts";
      case SilProtocolMessages.RECEIPTS -> "Receipts";
      case SilProtocolMessages.BLOCK_RANGE_UPDATE -> "BlockRangeUpdate";
      case SilProtocolMessages.GET_BLOCK_ACCESS_LISTS -> "GetBlockAccessLists";
      case SilProtocolMessages.BLOCK_ACCESS_LISTS -> "BlockAccessLists";
      default -> INVALID_MESSAGE_NAME;
    };
  }

  public static SilProtocol get() {
    return INSTANCE;
  }

  public static boolean isSil69Compatible(final Capability capability) {
    return NAME.equals(capability.getName()) && capability.getVersion() >= SIL69.getVersion();
  }

  public static boolean isSil70Compatible(final Capability capability) {
    return NAME.equals(capability.getName()) && capability.getVersion() >= SIL70.getVersion();
  }

  public static boolean isSil71Compatible(final Capability capability) {
    return NAME.equals(capability.getName()) && capability.getVersion() >= SIL71.getVersion();
  }
}
