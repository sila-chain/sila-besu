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
package org.hyperledger.besu.sila.silaMainnet;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.gascalculator.GasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.internal.SavmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.savm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.savm.processor.ContractCreationProcessor;
import org.hyperledger.besu.savm.processor.MessageCallProcessor;
import org.hyperledger.besu.sila.BlockValidator;
import org.hyperledger.besu.sila.GasLimitCalculator;
import org.hyperledger.besu.sila.chain.BadBlockManager;
import org.hyperledger.besu.sila.core.BlockHeaderFunctions;
import org.hyperledger.besu.sila.core.BlockImporter;
import org.hyperledger.besu.sila.silaMainnet.block.access.list.BlockAccessListFactory;
import org.hyperledger.besu.sila.silaMainnet.blockhash.PreExecutionProcessor;
import org.hyperledger.besu.sila.silaMainnet.feemarket.FeeMarket;
import org.hyperledger.besu.sila.silaMainnet.requests.ProhibitedRequestValidator;
import org.hyperledger.besu.sila.silaMainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.sila.silaMainnet.requests.RequestsValidator;
import org.hyperledger.besu.sila.silaMainnet.staterootcommitter.StateRootCommitterFactory;
import org.hyperledger.besu.sila.silaMainnet.transactionpool.TransactionPoolPreProcessor;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolSpecBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolSpecBuilder.class);

  private Supplier<GasCalculator> gasCalculatorBuilder;
  private GasLimitCalculatorBuilder gasLimitCalculatorBuilder;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;

  private BlockHeaderFunctions blockHeaderFunctions;
  private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator difficultyCalculator;
  private SavmConfiguration savmConfiguration;
  private BiFunction<GasCalculator, SavmConfiguration, SAVM> savmBuilder;
  private TransactionValidatorFactoryBuilder transactionValidatorFactoryBuilder;
  private BlockHeaderValidatorBuilder blockHeaderValidatorBuilder;
  private BlockHeaderValidatorBuilder ommerHeaderValidatorBuilder;
  private Function<ProtocolSchedule, BlockBodyValidator> blockBodyValidatorBuilder;
  private Function<SAVM, ContractCreationProcessor> contractCreationProcessorBuilder;
  private Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
      precompileContractRegistryBuilder;
  private BiFunction<SAVM, PrecompileContractRegistry, MessageCallProcessor>
      messageCallProcessorBuilder;
  private TransactionProcessorBuilder transactionProcessorBuilder;

  private BlockProcessorBuilder blockProcessorBuilder;
  private BlockValidatorBuilder blockValidatorBuilder;
  private Function<ProtocolSchedule, BlockAccessListValidator> blockAccessListValidatorBuilder =
      protocolSchedule -> BlockAccessListValidator.ALWAYS_REJECT_BAL;
  private BlockImporterBuilder blockImporterBuilder;

  private HardforkId hardforkId;
  private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  private WithdrawalsValidator withdrawalsValidator =
      new WithdrawalsValidator.ProhibitedWithdrawals();
  private WithdrawalsProcessor withdrawalsProcessor;
  private RequestsValidator requestsValidator = new ProhibitedRequestValidator();
  private RequestProcessorCoordinator requestProcessorCoordinator;
  protected PreExecutionProcessor preExecutionProcessor;
  private FeeMarketBuilder feeMarketBuilder = (__) -> FeeMarket.legacy();
  private BlobSchedule blobSchedule = new BlobSchedule.NoBlobSchedule();
  private BadBlockManager badBlockManager;
  private boolean isPoS = false;
  private boolean slotNumberRequired = false;
  private Duration slotDuration;
  private boolean isReplayProtectionSupported = false;
  private TransactionPoolPreProcessor transactionPoolPreProcessor;
  private BlockAccessListFactory blockAccessListFactory;
  private StateRootCommitterFactory stateRootCommitterFactory =
      new StateRootCommitterFactory(BalConfiguration.DISABLED);
  private BalConfiguration balConfiguration = BalConfiguration.DEFAULT;
  private BlockGasAccountingStrategy blockGasAccountingStrategy =
      BlockGasAccountingStrategy.FRONTIER;
  private BlockGasUsedValidator blockGasUsedValidator = BlockGasUsedValidator.FRONTIER;

  public ProtocolSpecBuilder gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder gasLimitCalculatorBuilder(
      final GasLimitCalculatorBuilder gasLimitCalculatorBuilder) {
    this.gasLimitCalculatorBuilder = gasLimitCalculatorBuilder;
    return this;
  }

  /**
   * Gets the current gas limit calculator builder.
   *
   * @return the gas limit calculator builder
   */
  public GasLimitCalculatorBuilder getGasLimitCalculatorBuilder() {
    return gasLimitCalculatorBuilder;
  }

  public ProtocolSpecBuilder blockReward(final Wei blockReward) {
    this.blockReward = blockReward;
    return this;
  }

  public ProtocolSpecBuilder skipZeroBlockRewards(final boolean skipZeroBlockRewards) {
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    return this;
  }

  public ProtocolSpecBuilder blockHeaderFunctions(final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    return this;
  }

  public ProtocolSpecBuilder transactionReceiptFactory(
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory) {
    this.transactionReceiptFactory = transactionReceiptFactory;
    return this;
  }

  public ProtocolSpecBuilder difficultyCalculator(final DifficultyCalculator difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
    return this;
  }

  public ProtocolSpecBuilder savmBuilder(
      final BiFunction<GasCalculator, SavmConfiguration, SAVM> savmBuilder) {
    this.savmBuilder = savmBuilder;
    return this;
  }

  public ProtocolSpecBuilder transactionValidatorFactoryBuilder(
      final TransactionValidatorFactoryBuilder transactionValidatorFactoryBuilder) {
    this.transactionValidatorFactoryBuilder = transactionValidatorFactoryBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockHeaderValidatorBuilder(
      final BlockHeaderValidatorBuilder blockHeaderValidatorBuilder) {
    this.blockHeaderValidatorBuilder = blockHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder ommerHeaderValidatorBuilder(
      final BlockHeaderValidatorBuilder ommerHeaderValidatorBuilder) {
    this.ommerHeaderValidatorBuilder = ommerHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockBodyValidatorBuilder(
      final Function<ProtocolSchedule, BlockBodyValidator> blockBodyValidatorBuilder) {
    this.blockBodyValidatorBuilder = blockBodyValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockAccessListValidatorBuilder(
      final Function<ProtocolSchedule, BlockAccessListValidator> blockAccessListValidatorBuilder) {
    this.blockAccessListValidatorBuilder = blockAccessListValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder contractCreationProcessorBuilder(
      final Function<SAVM, ContractCreationProcessor> contractCreationProcessorBuilder) {
    this.contractCreationProcessorBuilder = contractCreationProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder precompileContractRegistryBuilder(
      final Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
          precompileContractRegistryBuilder) {
    this.precompileContractRegistryBuilder =
        precompiledContractConfiguration -> {
          final PrecompileContractRegistry registry =
              precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
          return registry;
        };
    return this;
  }

  public ProtocolSpecBuilder messageCallProcessorBuilder(
      final BiFunction<SAVM, PrecompileContractRegistry, MessageCallProcessor>
          messageCallProcessorBuilder) {
    this.messageCallProcessorBuilder = messageCallProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder transactionProcessorBuilder(
      final TransactionProcessorBuilder transactionProcessorBuilder) {
    this.transactionProcessorBuilder = transactionProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockProcessorBuilder(
      final BlockProcessorBuilder blockProcessorBuilder) {
    this.blockProcessorBuilder = blockProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockImporterBuilder(final BlockImporterBuilder blockImporterBuilder) {
    this.blockImporterBuilder = blockImporterBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockValidatorBuilder(
      final BlockValidatorBuilder blockValidatorBuilder) {
    this.blockValidatorBuilder = blockValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder miningBeneficiaryCalculator(
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    return this;
  }

  public ProtocolSpecBuilder hardforkId(final HardforkId hardforkId) {
    this.hardforkId = hardforkId;
    return this;
  }

  public ProtocolSpecBuilder feeMarketBuilder(final FeeMarketBuilder feeMarketBuilder) {
    this.feeMarketBuilder = feeMarketBuilder;
    return this;
  }

  public ProtocolSpecBuilder blobSchedule(final BlobSchedule blobSchedule) {
    this.blobSchedule = blobSchedule;
    return this;
  }

  public ProtocolSpecBuilder badBlocksManager(final BadBlockManager badBlockManager) {
    this.badBlockManager = badBlockManager;
    return this;
  }

  public ProtocolSpecBuilder savmConfiguration(final SavmConfiguration savmConfiguration) {
    this.savmConfiguration = savmConfiguration;
    return this;
  }

  public ProtocolSpecBuilder withdrawalsValidator(final WithdrawalsValidator withdrawalsValidator) {
    this.withdrawalsValidator = withdrawalsValidator;
    return this;
  }

  public ProtocolSpecBuilder withdrawalsProcessor(final WithdrawalsProcessor withdrawalsProcessor) {
    this.withdrawalsProcessor = withdrawalsProcessor;
    return this;
  }

  public ProtocolSpecBuilder requestsValidator(
      final RequestsValidator requestsValidatorCoordinator) {
    this.requestsValidator = requestsValidatorCoordinator;
    return this;
  }

  public ProtocolSpecBuilder requestProcessorCoordinator(
      final RequestProcessorCoordinator requestProcessorCoordinator) {
    this.requestProcessorCoordinator = requestProcessorCoordinator;
    return this;
  }

  public ProtocolSpecBuilder preExecutionProcessor(
      final PreExecutionProcessor preExecutionProcessor) {
    this.preExecutionProcessor = preExecutionProcessor;
    return this;
  }

  public ProtocolSpecBuilder slotNumberRequired(final boolean slotNumberRequired) {
    this.slotNumberRequired = slotNumberRequired;
    return this;
  }

  public ProtocolSpecBuilder isPoS(final boolean isPoS) {
    this.isPoS = isPoS;
    return this;
  }

  public ProtocolSpecBuilder slotDuration(final Duration slotDuration) {
    this.slotDuration = slotDuration;
    return this;
  }

  public ProtocolSpecBuilder isReplayProtectionSupported(
      final boolean isReplayProtectionSupported) {
    this.isReplayProtectionSupported = isReplayProtectionSupported;
    return this;
  }

  public ProtocolSpecBuilder transactionPoolPreProcessor(
      final TransactionPoolPreProcessor transactionPoolPreProcessor) {
    this.transactionPoolPreProcessor = transactionPoolPreProcessor;
    return this;
  }

  public ProtocolSpecBuilder blockAccessListFactory(
      final BlockAccessListFactory blockAccessListFactory) {
    this.blockAccessListFactory = blockAccessListFactory;
    return this;
  }

  public ProtocolSpecBuilder stateRootCommitterFactory(
      final StateRootCommitterFactory stateRootCommitterFactory) {
    this.stateRootCommitterFactory = stateRootCommitterFactory;
    return this;
  }

  public ProtocolSpecBuilder balConfiguration(final BalConfiguration balConfiguration) {
    this.balConfiguration = balConfiguration;
    return this;
  }

  public ProtocolSpecBuilder blockGasAccountingStrategy(
      final BlockGasAccountingStrategy blockGasAccountingStrategy) {
    this.blockGasAccountingStrategy = blockGasAccountingStrategy;
    return this;
  }

  public ProtocolSpecBuilder blockGasUsedValidator(
      final BlockGasUsedValidator blockGasUsedValidator) {
    this.blockGasUsedValidator = blockGasUsedValidator;
    return this;
  }

  public ProtocolSpec build(final ProtocolSchedule protocolSchedule) {
    checkNotNull(gasCalculatorBuilder, "Missing gasCalculator");
    checkNotNull(gasLimitCalculatorBuilder, "Missing gasLimitCalculatorBuilder");
    checkNotNull(savmBuilder, "Missing operation registry");
    checkNotNull(savmConfiguration, "Missing savm configuration");
    checkNotNull(transactionValidatorFactoryBuilder, "Missing transaction validator");
    checkNotNull(contractCreationProcessorBuilder, "Missing contract creation processor");
    checkNotNull(precompileContractRegistryBuilder, "Missing precompile contract registry");
    checkNotNull(messageCallProcessorBuilder, "Missing message call processor");
    checkNotNull(transactionProcessorBuilder, "Missing transaction processor");
    checkNotNull(blockHeaderValidatorBuilder, "Missing block header validator");
    checkNotNull(blockBodyValidatorBuilder, "Missing block body validator");
    checkNotNull(blockAccessListValidatorBuilder, "Missing block access list validator");
    checkNotNull(blockProcessorBuilder, "Missing block processor");
    checkNotNull(blockImporterBuilder, "Missing block importer");
    checkNotNull(blockValidatorBuilder, "Missing block validator");
    checkNotNull(blockHeaderFunctions, "Missing block hash function");
    checkNotNull(blockReward, "Missing block reward");
    checkNotNull(difficultyCalculator, "Missing difficulty calculator");
    checkNotNull(transactionReceiptFactory, "Missing transaction receipt factory");
    checkNotNull(hardforkId, "Missing hardfork id");
    checkNotNull(miningBeneficiaryCalculator, "Missing Mining Beneficiary Calculator");
    checkNotNull(protocolSchedule, "Missing protocol schedule");
    checkNotNull(feeMarketBuilder, "Missing fee market");
    checkNotNull(badBlockManager, "Missing bad blocks manager");
    checkNotNull(blobSchedule, "Missing blob schedule");
    checkNotNull(slotDuration, "Missing slot duration");
    checkNotNull(balConfiguration, "Missing BAL configuration");

    final FeeMarket feeMarket = feeMarketBuilder.apply(blobSchedule);
    final GasCalculator gasCalculator = gasCalculatorBuilder.get();
    final GasLimitCalculator gasLimitCalculator =
        gasLimitCalculatorBuilder.apply(feeMarket, gasCalculator, blobSchedule);
    final SAVM savm = savmBuilder.apply(gasCalculator, savmConfiguration);
    LOGGER.debug(
        "Opcode optimizations {} for milestone {}",
        savm.getEvmConfiguration().enableOptimizedOpcodes() ? "enabled" : "disabled",
        hardforkId);
    final PrecompiledContractConfiguration precompiledContractConfiguration =
        new PrecompiledContractConfiguration(gasCalculator);
    final TransactionValidatorFactory transactionValidatorFactory =
        transactionValidatorFactoryBuilder.apply(savm, gasLimitCalculator, feeMarket);
    final ContractCreationProcessor contractCreationProcessor =
        contractCreationProcessorBuilder.apply(savm);
    final PrecompileContractRegistry precompileContractRegistry =
        precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
    final MessageCallProcessor messageCallProcessor =
        messageCallProcessorBuilder.apply(savm, precompileContractRegistry);
    final SilaMainnetTransactionProcessor transactionProcessor =
        transactionProcessorBuilder.apply(
            gasCalculator,
            feeMarket,
            transactionValidatorFactory,
            contractCreationProcessor,
            messageCallProcessor);

    final BlockHeaderValidator blockHeaderValidator =
        createBlockHeaderValidator(
            blockHeaderValidatorBuilder, feeMarket, gasCalculator, gasLimitCalculator);

    final BlockHeaderValidator ommerHeaderValidator =
        createBlockHeaderValidator(
            ommerHeaderValidatorBuilder, feeMarket, gasCalculator, gasLimitCalculator);

    final BlockBodyValidator blockBodyValidator = blockBodyValidatorBuilder.apply(protocolSchedule);

    BlockProcessor blockProcessor = createBlockProcessor(transactionProcessor, protocolSchedule);

    final boolean isStackedModeEnabled =
        savm.getEvmConfiguration().worldUpdaterMode() == WorldUpdaterMode.STACKED;

    final boolean balForkActivated = blockAccessListFactory != null;

    if (balForkActivated && !isStackedModeEnabled) {
      throw new IllegalStateException(
          "Block Access List (BAL) is activated by fork but world updater mode is not STACKED. "
              + "BAL requires STACKED world updater mode.");
    }

    final BlockAccessListValidator blockAccessListValidator =
        blockAccessListValidatorBuilder.apply(protocolSchedule);

    final BlockValidator blockValidator =
        blockValidatorBuilder.apply(
            blockHeaderValidator, blockBodyValidator, blockProcessor, blockAccessListValidator);
    final BlockImporter blockImporter = blockImporterBuilder.apply(blockValidator);

    return new ProtocolSpec(
        hardforkId,
        savm,
        transactionValidatorFactory,
        transactionProcessor,
        blockHeaderValidator,
        ommerHeaderValidator,
        blockBodyValidator,
        blockProcessor,
        blockImporter,
        blockValidator,
        blockHeaderFunctions,
        transactionReceiptFactory,
        difficultyCalculator,
        blockReward,
        miningBeneficiaryCalculator,
        precompileContractRegistry,
        skipZeroBlockRewards,
        gasCalculator,
        gasLimitCalculator,
        feeMarket,
        withdrawalsValidator,
        Optional.ofNullable(withdrawalsProcessor),
        requestsValidator,
        Optional.ofNullable(requestProcessorCoordinator),
        preExecutionProcessor,
        isPoS,
        slotNumberRequired,
        slotDuration,
        isReplayProtectionSupported,
        Optional.ofNullable(transactionPoolPreProcessor),
        Optional.ofNullable(blockAccessListFactory),
        blockAccessListValidator,
        stateRootCommitterFactory,
        blockGasAccountingStrategy,
        blockGasUsedValidator);
  }

  private BlockProcessor createBlockProcessor(
      final SilaMainnetTransactionProcessor transactionProcessor,
      final ProtocolSchedule protocolSchedule) {
    return blockProcessorBuilder.apply(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        protocolSchedule,
        balConfiguration);
  }

  private BlockHeaderValidator createBlockHeaderValidator(
      final BlockHeaderValidatorBuilder blockHeaderValidatorBuilder,
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator) {
    return blockHeaderValidatorBuilder
        .apply(feeMarket, gasCalculator, gasLimitCalculator)
        .difficultyCalculator(difficultyCalculator)
        .build();
  }

  @FunctionalInterface
  public interface TransactionProcessorBuilder {
    SilaMainnetTransactionProcessor apply(
        GasCalculator gasCalculator,
        FeeMarket feeMarket,
        TransactionValidatorFactory transactionValidatorFactory,
        ContractCreationProcessor contractCreationProcessor,
        MessageCallProcessor messageCallProcessor);
  }

  @FunctionalInterface
  public interface BlockProcessorBuilder {
    BlockProcessor apply(
        SilaMainnetTransactionProcessor transactionProcessor,
        AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
        Wei blockReward,
        MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        boolean skipZeroBlockRewards,
        ProtocolSchedule protocolSchedule,
        BalConfiguration balConfiguration);
  }

  @FunctionalInterface
  public interface BlockValidatorBuilder {
    BlockValidator apply(
        BlockHeaderValidator blockHeaderValidator,
        BlockBodyValidator blockBodyValidator,
        BlockProcessor blockProcessor,
        BlockAccessListValidator blockAccessListValidator);
  }

  @FunctionalInterface
  public interface FeeMarketBuilder {
    FeeMarket apply(BlobSchedule blobSchedule);
  }

  @FunctionalInterface
  public interface BlockHeaderValidatorBuilder {
    BlockHeaderValidator.Builder apply(
        FeeMarket feeMarket, GasCalculator gasCalculator, GasLimitCalculator gasLimitCalculator);
  }

  @FunctionalInterface
  public interface GasLimitCalculatorBuilder {
    GasLimitCalculator apply(
        FeeMarket feeMarket, GasCalculator gasCalculator, BlobSchedule blobSchedule);
  }

  @FunctionalInterface
  public interface BlockImporterBuilder {
    BlockImporter apply(BlockValidator blockValidator);
  }

  @FunctionalInterface
  public interface TransactionValidatorFactoryBuilder {
    TransactionValidatorFactory apply(
        SAVM savm, GasLimitCalculator gasLimitCalculator, FeeMarket feeMarket);
  }
}
