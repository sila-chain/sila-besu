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
package org.hyperledger.besu.sila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.sila.trie.pathbased.common.worldview.WorldStateConfig.createStatefulConfigWithTrie;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockDataGenerator;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderTestFixture;
import org.hyperledger.besu.sila.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.sila.sila-mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.sila.sila-mainnet.BalConfiguration;
import org.hyperledger.besu.sila.sila-mainnet.BlockAccessListValidator;
import org.hyperledger.besu.sila.sila-mainnet.BlockBodyValidator;
import org.hyperledger.besu.sila.sila-mainnet.BlockHeaderValidator;
import org.hyperledger.besu.sila.sila-mainnet.BlockProcessor;
import org.hyperledger.besu.sila.sila-mainnet.HeaderValidationMode;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetBlockHeaderFunctions;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetBlockProcessor;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetTransactionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpec;
import org.hyperledger.besu.sila.sila-mainnet.blockhash.FrontierPreExecutionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.sila.sila-mainnet.staterootcommitter.DefaultStateRootCommitterFactory;
import org.hyperledger.besu.sila.storage.StorageProvider;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.sila.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.sila.trie.pathbased.common.code.PathBasedCodeCache;
import org.hyperledger.besu.sila.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.sila.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BlockImportExceptionHandlingTest {

  private final SilaMainnetTransactionProcessor transactionProcessor =
      mock(SilaMainnetTransactionProcessor.class);
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
      mock(AbstractBlockProcessor.TransactionReceiptFactory.class);

  private final ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
  private final BlockProcessor blockProcessor =
      new SilaMainnetBlockProcessor(
          transactionProcessor,
          transactionReceiptFactory,
          Wei.ZERO,
          BlockHeader::getCoinbase,
          true,
          protocolSchedule,
          BalConfiguration.DEFAULT);
  private final BlockHeaderValidator blockHeaderValidator = mock(BlockHeaderValidator.class);
  private final BlockBodyValidator blockBodyValidator = mock(BlockBodyValidator.class);
  private final BlockAccessListValidator blockAccessListValidator =
      mock(BlockAccessListValidator.class);
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);
  private final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
  private final GasCalculator gasCalculator = mock(GasCalculator.class);
  private final GasLimitCalculator gasLimitCalculator = mock(GasLimitCalculator.class);
  private final FeeMarket feeMarket = mock(FeeMarket.class);
  protected final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();

  private final WorldStateStorageCoordinator worldStateStorageCoordinator =
      new WorldStateStorageCoordinator(
          new BonsaiWorldStateKeyValueStorage(
              storageProvider,
              new NoOpMetricsSystem(),
              DataStorageConfiguration.DEFAULT_BONSAI_CONFIG));

  private final WorldStateArchive worldStateArchive =
      // contains a BonsaiWorldState which we need to spy on.
      // do we need to also test with a DefaultWorldStateArchive?
      spy(InMemoryKeyValueStorageProvider.createBonsaiInMemoryWorldStateArchive(blockchain));

  private final BonsaiWorldState persisted =
      spy(
          new BonsaiWorldState(
              (BonsaiWorldStateProvider) worldStateArchive,
              (BonsaiWorldStateKeyValueStorage)
                  worldStateStorageCoordinator.worldStateKeyValueStorage(),
              SavmConfiguration.DEFAULT,
              createStatefulConfigWithTrie(),
              new PathBasedCodeCache()));

  private final BadBlockManager badBlockManager = new BadBlockManager();

  private BlockValidator silaMainnetBlockValidator;

  @BeforeEach
  public void setup() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getPreExecutionProcessor()).thenReturn(new FrontierPreExecutionProcessor());
    when(protocolSpec.getGasCalculator()).thenReturn(gasCalculator);
    when(protocolSpec.getGasLimitCalculator()).thenReturn(gasLimitCalculator);
    when(protocolSpec.getFeeMarket()).thenReturn(feeMarket);
    when(blockAccessListValidator.validate(any(), any(), anyInt())).thenReturn(true);
    when(protocolSpec.getStateRootCommitterFactory())
        .thenReturn(new DefaultStateRootCommitterFactory());
    silaMainnetBlockValidator =
        SilaMainnetBlockValidatorBuilder.frontier(
            blockHeaderValidator, blockBodyValidator, blockProcessor, blockAccessListValidator);
  }

  @Test
  void shouldNotBadBlockWhenInternalErrorDuringPersisting() {

    Mockito.doThrow(new StorageException("database problem")).when(persisted).persist(any(), any());
    Mockito.doReturn(persisted).when(worldStateArchive).getWorldState();
    Mockito.doReturn(Optional.of(persisted)).when(worldStateArchive).getWorldState(any());

    Block goodBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(0)
                    .hasTransactions(false)
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);

    when(blockBodyValidator.validateBody(
            eq(protocolContext),
            eq(goodBlock),
            any(),
            any(),
            eq(HeaderValidationMode.DETACHED_ONLY),
            any(),
            any()))
        .thenReturn(true);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
    silaMainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        goodBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  void shouldNotBadBlockWhenInternalErrorOnBlockLookup() {

    Block goodBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(0)
                    .hasTransactions(false)
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenThrow(new StorageException("database problem"));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);

    when(blockBodyValidator.validateBody(
            eq(protocolContext),
            eq(goodBlock),
            any(),
            any(),
            eq(HeaderValidationMode.DETACHED_ONLY),
            any(),
            any()))
        .thenReturn(true);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
    silaMainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        goodBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  void shouldNotBadBlockWhenInternalErrorDuringValidateHeader() {

    Block goodBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(0)
                    .hasTransactions(false)
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenThrow(new StorageException("database problem"));

    assertThat(badBlockManager.getBadBlocks()).isEmpty();
    silaMainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        goodBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  void shouldNotBadBlockWhenInternalErrorDuringValidateBody() {
    Mockito.doNothing().when(persisted).persist(any(), any());
    Mockito.doReturn(persisted).when(worldStateArchive).getWorldState();
    Mockito.doReturn(Optional.of(persisted)).when(worldStateArchive).getWorldState(any());

    Block goodBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockNumber(0)
                    .hasTransactions(false)
                    .setBlockHeaderFunctions(new SilaMainnetBlockHeaderFunctions()));

    when(blockchain.getBlockHeader(any(Hash.class)))
        .thenReturn(Optional.of(new BlockHeaderTestFixture().buildHeader()));
    when(blockHeaderValidator.validateHeader(
            any(BlockHeader.class),
            any(BlockHeader.class),
            eq(protocolContext),
            eq(HeaderValidationMode.DETACHED_ONLY)))
        .thenReturn(true);

    when(blockBodyValidator.validateBody(
            eq(protocolContext),
            eq(goodBlock),
            any(),
            any(),
            eq(HeaderValidationMode.DETACHED_ONLY),
            any(),
            any()))
        .thenThrow(new StorageException("database problem"));
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
    silaMainnetBlockValidator.validateAndProcessBlock(
        protocolContext,
        goodBlock,
        HeaderValidationMode.DETACHED_ONLY,
        HeaderValidationMode.DETACHED_ONLY);
    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }
}
