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
package org.hyperledger.besu.sila.sil.sync.common;

import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.sila.core.encoding.receipt.TransactionReceiptEncodingConfiguration.SIL69_RECEIPT_CONFIGURATION;
import static org.hyperledger.besu.sila.sil.core.Utils.blocksToSyncBlocks;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.SyncBlock;
import org.hyperledger.besu.sila.core.SyncBlockWithReceipts;
import org.hyperledger.besu.sila.sil.core.Utils;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ImportSyncBlocksStepTest {

  @Mock private ProtocolContext protocolContext;
  @Mock private MutableBlockchain blockchain;
  @Mock private SyncState syncState;
  private final BlockDataGenerator gen = new BlockDataGenerator();

  private ImportSyncBlocksStep importSyncBlocksStep;

  @BeforeEach
  public void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);

    importSyncBlocksStep =
        new ImportSyncBlocksStep(
            protocolContext, null, syncState, 0L, 10L, false, Optional.empty());
  }

  @Test
  public void shouldImportBlocks() {
    final List<Block> realBlocks = gen.blockSequence(5);
    final List<SyncBlock> blocks = blocksToSyncBlocks(realBlocks);
    final List<SyncBlockWithReceipts> blocksWithReceipts =
        blocks.stream()
            .map(
                block ->
                    new SyncBlockWithReceipts(
                        block,
                        Utils.receiptsToSyncReceipts(
                            gen.receipts(realBlocks.get(blocks.indexOf(block))),
                            SIL69_RECEIPT_CONFIGURATION)))
            .collect(toList());

    importSyncBlocksStep.accept(blocksWithReceipts);

    verify(blockchain).unsafeImportSyncBodiesAndReceipts(blocksWithReceipts, false);
    verify(syncState).setSyncProgress(0L, blocksWithReceipts.getLast().getNumber(), 10L);
  }
}
