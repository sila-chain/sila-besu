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
package org.hyperledger.besu.sila.sil.peervalidation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequiredBlocksPeerValidator extends AbstractPeerBlockValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RequiredBlocksPeerValidator.class);

  private final Hash hash;

  public RequiredBlocksPeerValidator(
      final ProtocolSchedule protocolSchedule,
      final PeerTaskExecutor peerTaskExecutor,
      final long blockNumber,
      final Hash hash,
      final long chainHeightEstimationBuffer) {
    super(protocolSchedule, peerTaskExecutor, blockNumber, chainHeightEstimationBuffer);
    this.hash = hash;
  }

  public RequiredBlocksPeerValidator(
      final ProtocolSchedule protocolSchedule,
      final PeerTaskExecutor peerTaskExecutor,
      final long blockNumber,
      final Hash hash) {
    this(
        protocolSchedule,
        peerTaskExecutor,
        blockNumber,
        hash,
        DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER);
  }

  @Override
  boolean validateBlockHeader(final SilPeer silPeer, final BlockHeader header) {
    final boolean validBlock = hash.equals(header.getHash());
    if (!validBlock) {
      LOG.debug(
          "Peer {} is invalid because required block ({}) does not match required hash ({}).",
          silPeer,
          blockNumber,
          hash);
    }
    return validBlock;
  }
}
