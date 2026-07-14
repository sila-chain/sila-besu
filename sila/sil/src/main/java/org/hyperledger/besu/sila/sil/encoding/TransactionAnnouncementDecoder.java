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
package org.hyperledger.besu.sila.sil.encoding;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.sila.sil.transactions.TransactionAnnouncement;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.rlp.RLPException;
import org.hyperledger.besu.sila.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;

public class TransactionAnnouncementDecoder {

  @FunctionalInterface
  public interface Decoder {
    List<TransactionAnnouncement> decode(RLPInput input);
  }

  /**
   * Returns the correct decoder given an Sil Capability
   *
   * <p>See <a href="https://sips.sila.org/SIPS/sip-5793">SIP-5793</a>
   *
   * @param capability the version of the sil protocol
   * @return the correct decoder
   */
  public static Decoder getDecoder(final Capability capability) {
    return TransactionAnnouncementDecoder::decodeForSil68;
  }

  /**
   * Decode the list of transactions in the NewPooledTransactionHashesMessage
   *
   * @param input input used to decode the NewPooledTransactionHashesMessage after Sil/68
   *     <p>format: [[type_0: B_1, type_1: B_1, ...], [size_0: P, size_1: P, ...], ...]
   * @return the list of TransactionAnnouncement decoded from the message with size, type and hash
   */
  private static List<TransactionAnnouncement> decodeForSil68(final RLPInput input) {
    final int size = input.enterList();

    final List<TransactionType> types = new ArrayList<>(size);
    final byte[] bytes = input.readBytes().toArray();
    for (final byte b : bytes) {
      final var transactionType =
          TransactionType.fromSilSerializedType(b)
              .orElseThrow(
                  () ->
                      new RLPException(
                          "Invalid transaction type 0x%02x".formatted(Byte.toUnsignedInt(b))));
      types.add(transactionType);
    }

    final List<Long> sizes = input.readList(RLPInput::readUnsignedIntScalar);

    // use Bytes32::copy to avoid keeping reference to underlying RLP byte array
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32().copy()));
    input.leaveList();
    if (!(types.size() == hashes.size() && hashes.size() == sizes.size())) {
      throw new RLPException("Hashes, sizes and types must have the same number of elements");
    }
    return TransactionAnnouncement.create(types, sizes, hashes);
  }
}
