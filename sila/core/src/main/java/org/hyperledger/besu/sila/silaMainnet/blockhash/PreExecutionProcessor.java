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
package org.hyperledger.besu.sila.silaMainnet.blockhash;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.savm.blockhash.BlockHashLookup;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.ProcessableBlockHeader;
import org.hyperledger.besu.sila.silaMainnet.systemcall.BlockContextProcessor;
import org.hyperledger.besu.sila.silaMainnet.systemcall.BlockProcessingContext;

import java.util.Optional;

/**
 * A processor that updates the state before transaction execution. This is used to implement
 * pre-execution logic defined in Sila upgrades such as SIP-2935 (access to historical block hashes)
 * and SIP-4788 (exposing the beacon chain root). *
 *
 * <p>This processor is invoked once per block, before any transactions are executed.
 */
public interface PreExecutionProcessor extends BlockContextProcessor<Void, BlockProcessingContext> {

  BlockHashLookup createBlockHashLookup(Blockchain blockchain, ProcessableBlockHeader blockHeader);

  default Optional<Address> getHistoryContract() {
    return Optional.empty();
  }

  default Optional<Address> getBeaconRootsContract() {
    return Optional.empty();
  }
}
