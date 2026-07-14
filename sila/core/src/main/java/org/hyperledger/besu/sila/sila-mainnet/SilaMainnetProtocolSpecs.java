/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.sila.sila-mainnet;

import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.AMSTERDAM;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.ARROW_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BERLIN;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BPO1;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BPO2;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BPO3;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BPO4;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BPO5;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.BYZANTIUM;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.CANCUN;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.CONSTANTINOPLE;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.DAO_RECOVERY_INIT;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.DAO_RECOVERY_TRANSITION;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.EXPERIMENTAL_SIPS;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.FRONTIER;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.FUTURE_SIPS;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.GRAY_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.HOMESTEAD;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.ISTANBUL;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.LONDON;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.MUIR_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.OSAKA;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.PARIS;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.PETERSBURG;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.PRAGUE;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.SHANGHAI;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.SPURIOUS_DRAGON;
import static org.hyperledger.besu.datatypes.HardforkId.SilaMainnetHardforkId.TANGERINE_WHISTLE;
import static org.hyperledger.besu.sila.sila-mainnet.requests.SilaMainnetRequestsProcessor.pragueRequestsProcessors;

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.config.BlobScheduleOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.BlockProcessingResult;
import org.hyperledger.besu.sila.SilaMainnetBlockValidatorBuilder;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.core.TransactionReceipt;
import org.hyperledger.besu.sila.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.sila.sila-mainnet.AbstractBlockProcessor.TransactionReceiptFactory;
import org.hyperledger.besu.sila.sila-mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.sila.sila-mainnet.block.access.list.BlockAccessListFactory;
import org.hyperledger.besu.sila.sila-mainnet.blockhash.SilaCancunPreExecutionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.blockhash.FrontierPreExecutionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.blockhash.SilaPraguePreExecutionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.blockhash.PreExecutionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.sila.sila-mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.sila.sila-mainnet.parallelization.SilaMainnetParallelBlockProcessor;
import org.hyperledger.besu.sila.sila-mainnet.requests.SilaMainnetRequestsValidator;
import org.hyperledger.besu.sila.sila-mainnet.requests.RequestContractAddresses;
import org.hyperledger.besu.sila.sila-mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.sila.sila-mainnet.staterootcommitter.BalStateRootCommitterFactory;
import org.hyperledger.besu.sila.sila-mainnet.transactionpool.SilaOsakaTransactionPoolPreProcessor;
import org.hyperledger.besu.sila.processing.TransactionProcessingResult;
import org.hyperledger.besu.savm.SilaMainnetSAVMs;
import org.hyperledger.besu.savm.account.MutableAccount;
import org.hyperledger.besu.savm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.savm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.savm.gascalculator.SilaAmsterdamGasCalculator;
import org.hyperledger.besu.savm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.savm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaCancunGasCalculator;
import org.hyperledger.besu.savm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.savm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.savm.gascalculator.HomesteadGasCalculator;
import org.hyperledger.besu.savm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.savm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaOsakaGasCalculator;
import org.hyperledger.besu.savm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaPragueGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SilaShanghaiGasCalculator;
import org.hyperledger.besu.savm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.savm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.savm.internal.SavmConfiguration;
import org.hyperledger.besu.savm.log.SIP7708TransferLogEmitter;
import org.hyperledger.besu.savm.processor.ContractCreationProcessor;
import org.hyperledger.besu.savm.processor.MessageCallProcessor;
import org.hyperledger.besu.savm.worldstate.CodeDelegationService;
import org.hyperledger.besu.savm.worldstate.WorldState;
import org.hyperledger.besu.savm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.google.common.io.Resources;
import io.vertx.core.json.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides the various {@link ProtocolSpec}s on sila-mainnet hard forks. */
public abstract class SilaMainnetProtocolSpecs {

  private static final Address RIPEMD160_PRECOMPILE =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  private static final SignatureAlgorithm SIGNATURE_ALGORITHM =
      SignatureAlgorithmFactory.getInstance();

  // A consensus bug at Sila sila-mainnet transaction 0xcf416c53
  // deleted an empty account even when the message execution scope
  // failed, but the transaction itself succeeded.
  private static final HashSet<Address> SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES;

  private static final Wei FRONTIER_BLOCK_REWARD = Wei.fromSil(5);

  private static final Wei BYZANTIUM_BLOCK_REWARD = Wei.fromSil(3);

  private static final Wei CONSTANTINOPLE_BLOCK_REWARD = Wei.fromSil(2);

  private static final Logger LOG = LoggerFactory.getLogger(SilaMainnetProtocolSpecs.class);
  private static final int POW_SLOT_TIME_ESTIMATION = 13;

  static {
    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES = new HashSet<>();
    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES.add(RIPEMD160_PRECOMPILE);
  }

  private SilaMainnetProtocolSpecs() {}

  public static ProtocolSpecBuilder frontierDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return new ProtocolSpecBuilder()
        .gasCalculator(FrontierGasCalculator::new)
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) -> new FrontierTargetingGasLimitCalculator())
        .savmBuilder(SilaMainnetSAVMs::frontier)
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::frontier)
        .messageCallProcessorBuilder(MessageCallProcessor::new)
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm, false, Collections.singletonList(MaxCodeSizeRule.from(savm)), 0))
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(), gasLimitCalculator, false, Optional.empty()))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(false)
                    .warmCoinbase(false)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(FeeMarket.legacy())
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .difficultyCalculator(SilaMainnetDifficultyCalculators.FRONTIER)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) -> SilaMainnetBlockHeaderValidator.create())
        .ommerHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                SilaMainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator())
        .blockBodyValidatorBuilder(SilaMainnetBlockBodyValidator::new)
        .blockAccessListValidatorBuilder(__ -> BlockAccessListValidator.ALWAYS_REJECT_BAL)
        .transactionReceiptFactory(new FrontierTransactionReceiptFactory())
        .blockReward(FRONTIER_BLOCK_REWARD)
        .skipZeroBlockRewards(false)
        .balConfiguration(balConfiguration)
        .blockProcessorBuilder(
            isParallelTxProcessingEnabled
                ? new SilaMainnetParallelBlockProcessor.ParallelBlockProcessorBuilder(metricsSystem)
                : new SilaMainnetBlockProcessor.SilaMainnetBlockProcessorBuilder(metricsSystem))
        .blockValidatorBuilder(SilaMainnetBlockValidatorBuilder::frontier)
        .blockImporterBuilder(SilaMainnetBlockImporter::new)
        .blockHeaderFunctions(new SilaMainnetBlockHeaderFunctions())
        .miningBeneficiaryCalculator(BlockHeader::getCoinbase)
        .savmConfiguration(savmConfiguration)
        .preExecutionProcessor(new FrontierPreExecutionProcessor())
        .slotDuration(getSlotDurationFromGenesis(genesisConfigOptions))
        .hardforkId(FRONTIER);
  }

  private static Duration getSlotDurationFromGenesis(
      final GenesisConfigOptions genesisConfigOptions) {

    if (genesisConfigOptions.isIbft2()) {
      return Duration.ofSeconds(genesisConfigOptions.getBftConfigOptions().getBlockPeriodSeconds());
    }
    if (genesisConfigOptions.isQbft()) {
      return Duration.ofSeconds(
          genesisConfigOptions.getQbftConfigOptions().getBlockPeriodSeconds());
    }
    if (genesisConfigOptions.isClique()) {
      return Duration.ofSeconds(
          genesisConfigOptions.getCliqueConfigOptions().getBlockPeriodSeconds());
    }
    // if no hints are present in the genesis file, then we are in PoW and there is not predefined
    // block period, so just return an estimation.
    // We also get here if we are in PoS mode, but the right value for PoS slot duration will
    // override this value.
    return Duration.ofSeconds(POW_SLOT_TIME_ESTIMATION);
  }

  public static ProtocolSpecBuilder homesteadDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return frontierDefinition(
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(HomesteadGasCalculator::new)
        .savmBuilder(SilaMainnetSAVMs::homestead)
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm, true, Collections.singletonList(MaxCodeSizeRule.from(savm)), 0))
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(), gasLimitCalculator, true, Optional.empty()))
        .difficultyCalculator(SilaMainnetDifficultyCalculators.HOMESTEAD)
        .hardforkId(HOMESTEAD);
  }

  public static ProtocolSpecBuilder daoRecoveryInitDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return homesteadDefinition(
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                SilaMainnetBlockHeaderValidator.createDaoValidator())
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                protocolSchedule,
                balConfig) ->
                new DaoBlockProcessor(
                    isParallelTxProcessingEnabled
                        ? new SilaMainnetParallelBlockProcessor(
                            transactionProcessor,
                            transactionReceiptFactory,
                            blockReward,
                            miningBeneficiaryCalculator,
                            skipZeroBlockRewards,
                            protocolSchedule,
                            balConfig,
                            metricsSystem)
                        : new SilaMainnetBlockProcessor(
                            transactionProcessor,
                            transactionReceiptFactory,
                            blockReward,
                            miningBeneficiaryCalculator,
                            skipZeroBlockRewards,
                            protocolSchedule,
                            balConfig,
                            metricsSystem)))
        .hardforkId(DAO_RECOVERY_INIT);
  }

  public static ProtocolSpecBuilder daoRecoveryTransitionDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return daoRecoveryInitDefinition(
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .blockProcessorBuilder(
            isParallelTxProcessingEnabled
                ? new SilaMainnetParallelBlockProcessor.ParallelBlockProcessorBuilder(metricsSystem)
                : new SilaMainnetBlockProcessor.SilaMainnetBlockProcessorBuilder(metricsSystem))
        .hardforkId(DAO_RECOVERY_TRANSITION);
  }

  public static ProtocolSpecBuilder tangerineWhistleDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return homesteadDefinition(
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .hardforkId(TANGERINE_WHISTLE);
  }

  public static ProtocolSpecBuilder spuriousDragonDefinition(
      final Optional<BigInteger> chainId,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return tangerineWhistleDefinition(
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .isReplayProtectionSupported(true)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
        .messageCallProcessorBuilder(
            (savm, precompileContractRegistry) ->
                new MessageCallProcessor(
                    savm,
                    precompileContractRegistry,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.from(savm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(), gasLimitCalculator, true, chainId))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidator)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(false)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .hardforkId(SPURIOUS_DRAGON);
  }

  public static ProtocolSpecBuilder byzantiumDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return spuriousDragonDefinition(
            chainId,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(ByzantiumGasCalculator::new)
        .savmBuilder(SilaMainnetSAVMs::byzantium)
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(SilaMainnetDifficultyCalculators.BYZANTIUM)
        .transactionReceiptFactory(new ByzantiumTransactionReceiptFactory(enableRevertReason))
        .blockReward(BYZANTIUM_BLOCK_REWARD)
        .hardforkId(BYZANTIUM);
  }

  public static ProtocolSpecBuilder constantinopleDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return byzantiumDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .difficultyCalculator(SilaMainnetDifficultyCalculators.CONSTANTINOPLE)
        .gasCalculator(ConstantinopleGasCalculator::new)
        .savmBuilder(SilaMainnetSAVMs::constantinople)
        .blockReward(CONSTANTINOPLE_BLOCK_REWARD)
        .hardforkId(CONSTANTINOPLE);
  }

  public static ProtocolSpecBuilder petersburgDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return constantinopleDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(PetersburgGasCalculator::new)
        .hardforkId(PETERSBURG);
  }

  public static ProtocolSpecBuilder istanbulDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return petersburgDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(IstanbulGasCalculator::new)
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.istanbul(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::istanbul)
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.from(savm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .hardforkId(ISTANBUL);
  }

  static ProtocolSpecBuilder muirGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return istanbulDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .difficultyCalculator(SilaMainnetDifficultyCalculators.MUIR_GLACIER)
        .hardforkId(MUIR_GLACIER);
  }

  static ProtocolSpecBuilder berlinDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return muirGlacierDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(BerlinGasCalculator::new)
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(),
                    gasLimitCalculator,
                    true,
                    chainId,
                    Set.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST)))
        .transactionReceiptFactory(new BerlinTransactionReceiptFactory(enableRevertReason))
        .hardforkId(BERLIN);
  }

  static ProtocolSpecBuilder londonDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    final long londonForkBlockNumber =
        genesisConfigOptions.getLondonBlockNumber().orElse(Long.MAX_VALUE);
    return berlinDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .feeMarketBuilder(
            createFeeMarket(
                londonForkBlockNumber,
                genesisConfigOptions.isZeroBaseFee(),
                genesisConfigOptions.isFixedBaseFee(),
                false,
                miningConfiguration.getMinTransactionGasPrice(),
                (blobSchedule) ->
                    FeeMarket.london(
                        londonForkBlockNumber, genesisConfigOptions.getBaseFeePerGas())))
        .gasCalculator(LondonGasCalculator::new)
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) ->
                new LondonTargetingGasLimitCalculator(
                    londonForkBlockNumber, (BaseFeeMarket) feeMarket))
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.SIP1559),
                    Integer.MAX_VALUE))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(false)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.sip1559())
                    .build())
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm,
                    true,
                    List.of(MaxCodeSizeRule.from(savm), PrefixCodeRule.of()),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.london(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .difficultyCalculator(SilaMainnetDifficultyCalculators.LONDON)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                SilaMainnetBlockHeaderValidator.createBaseFeeMarketValidator((BaseFeeMarket) feeMarket))
        .ommerHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                SilaMainnetBlockHeaderValidator.createBaseFeeMarketOmmerValidator(
                    (BaseFeeMarket) feeMarket))
        .blockBodyValidatorBuilder(BaseFeeBlockBodyValidator::new)
        .hardforkId(LONDON);
  }

  static ProtocolSpecBuilder arrowGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return londonDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .difficultyCalculator(SilaMainnetDifficultyCalculators.ARROW_GLACIER)
        .hardforkId(ARROW_GLACIER);
  }

  static ProtocolSpecBuilder grayGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return arrowGlacierDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .difficultyCalculator(SilaMainnetDifficultyCalculators.GRAY_GLACIER)
        .hardforkId(GRAY_GLACIER);
  }

  static ProtocolSpecBuilder parisDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {

    return grayGlacierDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.paris(gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .difficultyCalculator(SilaMainnetDifficultyCalculators.PROOF_OF_STAKE_DIFFICULTY)
        .blockHeaderValidatorBuilder(SilaMainnetBlockHeaderValidator::mergeBlockHeaderValidator)
        .blockReward(Wei.ZERO)
        .skipZeroBlockRewards(true)
        .isPoS(true)
        .slotDuration(Duration.ofSeconds(miningConfiguration.getUnstable().getPosSlotDuration()))
        .hardforkId(PARIS);
  }

  static ProtocolSpecBuilder shanghaiDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return parisDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        // gas calculator has new code to support SIP-3860 limit and meter initcode
        .gasCalculator(SilaShanghaiGasCalculator::new)
        // SAVM has a new operation for SIP-3855 PUSH0 instruction
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.shanghai(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        // we need to flip the Warm Coinbase flag for SIP-3651 warm coinbase
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.sip1559())
                    .build())
        // Contract creation rules for SIP-3860 Limit and meter intitcode
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.SIP1559),
                    savm.getMaxInitcodeSize()))
        .withdrawalsProcessor(new WithdrawalsProcessor())
        .withdrawalsValidator(new WithdrawalsValidator.AllowedWithdrawals())
        .blockHeaderValidatorBuilder(SilaMainnetBlockHeaderValidator::noBlobBlockHeaderValidator)
        .hardforkId(SHANGHAI);
  }

  static ProtocolSpecBuilder cancunDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    final long londonForkBlockNumber = genesisConfigOptions.getLondonBlockNumber().orElse(0L);

    return shanghaiDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .feeMarketBuilder(
            createFeeMarket(
                londonForkBlockNumber,
                genesisConfigOptions.isZeroBaseFee(),
                genesisConfigOptions.isFixedBaseFee(),
                true,
                miningConfiguration.getMinTransactionGasPrice(),
                (blobSchedule) ->
                    FeeMarket.cancun(
                        londonForkBlockNumber,
                        genesisConfigOptions.getBaseFeePerGas(),
                        blobSchedule)))
        .blobSchedule(
            genesisConfigOptions
                .getBlobScheduleOptions()
                .flatMap(BlobScheduleOptions::getSilaCancun)
                .orElse(BlobSchedule.CANCUN_DEFAULT))
        // gas calculator for SIP-4844 blob gas
        .gasCalculator(SilaCancunGasCalculator::new)
        // gas limit with SIP-4844 max blob gas per block
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) ->
                new SilaCancunTargetingGasLimitCalculator(
                    londonForkBlockNumber,
                    (BaseFeeMarket) feeMarket,
                    gasCalculator,
                    blobSchedule.getMax(),
                    blobSchedule.getTarget()))
        // SAVM changes to support SIP-1153: TSTORE and SIP-5656: MCOPY
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.cancun(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        // use SilaCancun fee market
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidator)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.sip1559())
                    .build())
        // change to check for max blob gas per block for SIP-4844
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.SIP1559,
                        TransactionType.BLOB),
                    Set.of(BlobType.KZG_PROOF),
                    savm.getMaxInitcodeSize()))
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::cancun)
        .blockHeaderValidatorBuilder(SilaMainnetBlockHeaderValidator::blobAwareBlockHeaderValidator)
        .preExecutionProcessor(getPreExecutionProcessor(genesisConfigOptions))
        .hardforkId(CANCUN);
  }

  private static PreExecutionProcessor getPreExecutionProcessor(
      final GenesisConfigOptions genesisConfigOptions) {
    if (isPoAConsensus(genesisConfigOptions) && !hasSystemContractAddresses(genesisConfigOptions)) {
      return new FrontierPreExecutionProcessor();
    }

    return new SilaCancunPreExecutionProcessor();
  }

  private static PreExecutionProcessor getSilaPraguePreExecutionProcessor(
      final GenesisConfigOptions genesisConfigOptions) {
    if (isPoAConsensus(genesisConfigOptions) && !hasSystemContractAddresses(genesisConfigOptions)) {
      return new FrontierPreExecutionProcessor();
    }

    return new SilaPraguePreExecutionProcessor();
  }

  static ProtocolSpecBuilder pragueDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder pragueSpecBuilder =
        cancunDefinition(
                chainId,
                enableRevertReason,
                genesisConfigOptions,
                savmConfiguration,
                miningConfiguration,
                isParallelTxProcessingEnabled,
                balConfiguration,
                metricsSystem)
            .blobSchedule(
                genesisConfigOptions
                    .getBlobScheduleOptions()
                    .flatMap(BlobScheduleOptions::getSilaPrague)
                    .orElse(BlobSchedule.PRAGUE_DEFAULT))
            .gasCalculator(SilaPragueGasCalculator::new)
            .savmBuilder(
                (gasCalculator, jdCacheConfig) ->
                    SilaMainnetSAVMs.prague(
                        gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))

            // SIP-2537 BLS12-381 precompiles
            .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::prague)

            // SIP-7002 Withdrawals / SIP-6610 Deposits / SIP-7685 Requests
            .requestsValidator(new SilaMainnetRequestsValidator())

            // change to accept SIP-7702 transactions
            .transactionValidatorFactoryBuilder(
                (savm, gasLimitCalculator, feeMarket) ->
                    new TransactionValidatorFactory(
                        savm.getGasCalculator(),
                        gasLimitCalculator,
                        feeMarket,
                        true,
                        chainId,
                        Set.of(
                            TransactionType.FRONTIER,
                            TransactionType.ACCESS_LIST,
                            TransactionType.SIP1559,
                            TransactionType.BLOB,
                            TransactionType.DELEGATE_CODE),
                        Set.of(BlobType.KZG_PROOF),
                        savm.getMaxInitcodeSize()))
            // CodeDelegationProcessor
            .transactionProcessorBuilder(
                (gasCalculator,
                    feeMarket,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor) ->
                    SilaMainnetTransactionProcessor.builder()
                        .gasCalculator(gasCalculator)
                        .transactionValidatorFactory(transactionValidator)
                        .contractCreationProcessor(contractCreationProcessor)
                        .messageCallProcessor(messageCallProcessor)
                        .clearEmptyAccounts(true)
                        .warmCoinbase(true)
                        .maxStackSize(savmConfiguration.savmStackSize())
                        .feeMarket(feeMarket)
                        .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.sip1559())
                        .codeDelegationProcessor(
                            new CodeDelegationProcessor(
                                chainId,
                                SIGNATURE_ALGORITHM.getHalfCurveOrder(),
                                new CodeDelegationService()))
                        .build())
            // SIP-2935 Blockhash processor
            .preExecutionProcessor(getSilaPraguePreExecutionProcessor(genesisConfigOptions))
            .hardforkId(PRAGUE);
    if (isPoAConsensus(genesisConfigOptions) && !hasSystemContractAddresses(genesisConfigOptions)) {
      LOG.warn(
          "Skipping system contract request processors for PoA consensus (clique/ibft/qbft) without system contract addresses.");
      pragueSpecBuilder.requestProcessorCoordinator(RequestProcessorCoordinator.noOp());
    } else {
      try {
        RequestContractAddresses requestContractAddresses =
            RequestContractAddresses.fromGenesis(genesisConfigOptions);

        pragueSpecBuilder.requestProcessorCoordinator(
            pragueRequestsProcessors(requestContractAddresses));
      } catch (NoSuchElementException nsee) {
        LOG.warn("SilaPrague definitions require system contract addresses in genesis");
        throw nsee;
      }
    }

    return pragueSpecBuilder;
  }

  private static boolean isPoAConsensus(final GenesisConfigOptions genesisConfigOptions) {
    return genesisConfigOptions.isClique()
        || genesisConfigOptions.isIbft2()
        || genesisConfigOptions.isQbft();
  }

  private static boolean hasSystemContractAddresses(
      final GenesisConfigOptions genesisConfigOptions) {
    return genesisConfigOptions.getDepositContractAddress().isPresent()
        && genesisConfigOptions.getWithdrawalRequestContractAddress().isPresent()
        && genesisConfigOptions.getConsolidationRequestContractAddress().isPresent();
  }

  static ProtocolSpecBuilder osakaDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    final long londonForkBlockNumber = genesisConfigOptions.getLondonBlockNumber().orElse(0L);

    return pragueDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(SilaOsakaGasCalculator::new)
        // tx gas limit cap SIP-7825
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) ->
                new SilaOsakaTargetingGasLimitCalculator(
                    londonForkBlockNumber,
                    (BaseFeeMarket) feeMarket,
                    gasCalculator,
                    blobSchedule.getMax(),
                    blobSchedule.getTarget(),
                    miningConfiguration.getMaxBlobsPerTransaction(),
                    miningConfiguration.getMaxBlobsPerBlock()))
        .savmBuilder(
            (gasCalculator, __) ->
                SilaMainnetSAVMs.osaka(gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .transactionValidatorFactoryBuilder(
            (savm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    savm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.SIP1559,
                        TransactionType.BLOB,
                        TransactionType.DELEGATE_CODE),
                    Set.of(BlobType.KZG_CELL_PROOFS),
                    savm.getMaxInitcodeSize()))
        .transactionPoolPreProcessor(new SilaOsakaTransactionPoolPreProcessor())
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::osaka)
        .blockValidatorBuilder(SilaMainnetBlockValidatorBuilder::osaka)
        .hardforkId(OSAKA);
  }

  static ProtocolSpecBuilder bpo1Definition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder builder =
        osakaDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem);
    return applyBlobSchedule(
        builder,
        genesisConfigOptions,
        BlobScheduleOptions::getBpo1,
        GenesisConfigOptions::getBpo1Time,
        BPO1);
  }

  static ProtocolSpecBuilder bpo2Definition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder builder =
        bpo1Definition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem);
    return applyBlobSchedule(
        builder,
        genesisConfigOptions,
        BlobScheduleOptions::getBpo2,
        GenesisConfigOptions::getBpo2Time,
        BPO2);
  }

  static ProtocolSpecBuilder bpo3Definition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder builder =
        bpo2Definition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem);
    return applyBlobSchedule(
        builder,
        genesisConfigOptions,
        BlobScheduleOptions::getBpo3,
        GenesisConfigOptions::getBpo3Time,
        BPO3);
  }

  static ProtocolSpecBuilder bpo4Definition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder builder =
        bpo3Definition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem);
    return applyBlobSchedule(
        builder,
        genesisConfigOptions,
        BlobScheduleOptions::getBpo4,
        GenesisConfigOptions::getBpo4Time,
        BPO4);
  }

  static ProtocolSpecBuilder bpo5Definition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    ProtocolSpecBuilder builder =
        bpo4Definition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem);
    return applyBlobSchedule(
        builder,
        genesisConfigOptions,
        BlobScheduleOptions::getBpo5,
        GenesisConfigOptions::getBpo5Time,
        BPO5);
  }

  static ProtocolSpecBuilder amsterdamDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return bpo5Definition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(SilaAmsterdamGasCalculator::new)
        // SIP-7708: Override savmBuilder to use SilaAmsterdam SAVM with transfer logging
        .savmBuilder(
            (gasCalculator, __) ->
                SilaMainnetSAVMs.amsterdam(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        // SIP-7708: ContractCreationProcessor with transfer log emission enabled
        .contractCreationProcessorBuilder(
            savm ->
                new ContractCreationProcessor(
                    savm,
                    true,
                    List.of(MaxCodeSizeRule.from(savm), PrefixCodeRule.of()),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES,
                    SIP7708TransferLogEmitter.INSTANCE))
        // SIP-7708: MessageCallProcessor with transfer log emission enabled
        .messageCallProcessorBuilder(
            (savm, precompileContractRegistry) ->
                new MessageCallProcessor(
                    savm,
                    precompileContractRegistry,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES,
                    SIP7708TransferLogEmitter.INSTANCE))
        // SIP-7708: TransactionProcessor configured for SilaAmsterdam with transfer log emission
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                SilaMainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidator)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(savmConfiguration.savmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.sip1559())
                    .codeDelegationProcessor(
                        new CodeDelegationProcessor(
                            chainId,
                            SIGNATURE_ALGORITHM.getHalfCurveOrder(),
                            new CodeDelegationService()))
                    .transferLogEmitter(SIP7708TransferLogEmitter.INSTANCE)
                    .build())
        .blockAccessListFactory(new BlockAccessListFactory())
        .blockAccessListValidatorBuilder(SilaMainnetBlockAccessListValidator::create)
        .stateRootCommitterFactory(new BalStateRootCommitterFactory(balConfiguration))
        // SIP-8037: Disable validation-time TX_MAX_GAS_LIMIT cap (enforced at runtime on regular
        // gas)
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) -> {
              final long londonForkBlock = genesisConfigOptions.getLondonBlockNumber().orElse(0L);
              return new SilaAmsterdamTargetingGasLimitCalculator(
                  londonForkBlock,
                  (BaseFeeMarket) feeMarket,
                  gasCalculator,
                  blobSchedule.getMax(),
                  blobSchedule.getTarget(),
                  miningConfiguration.getMaxBlobsPerTransaction(),
                  miningConfiguration.getMaxBlobsPerBlock());
            })
        // SIP-8037: SilaAmsterdam gas calculator with state gas cost support
        .gasCalculator(SilaAmsterdamGasCalculator::new)
        // SilaAmsterdam (SIP-7778 + SIP-8037): Pre-refund 2D gas accounting
        .blockGasAccountingStrategy(BlockGasAccountingStrategy.AMSTERDAM)
        // SilaAmsterdam: Validator uses pre-refund gas_metered = max(regular, state) from processing
        .blockGasUsedValidator(BlockGasUsedValidator.AMSTERDAM)
        .hardforkId(AMSTERDAM);
  }

  private static ProtocolSpecBuilder applyBlobSchedule(
      final ProtocolSpecBuilder builder,
      final GenesisConfigOptions genesisConfigOptions,
      final Function<BlobScheduleOptions, Optional<BlobSchedule>> blobGetter,
      final Function<GenesisConfigOptions, OptionalLong> blobScheduleTimestampGetter,
      final HardforkId hardforkId) {
    // Only apply a fork's blob schedule if the fork is actually activated (has a timestamp).
    // This prevents inactive BPO forks from overriding the blob schedule with stale values
    // from the genesis config.
    if (blobScheduleTimestampGetter.apply(genesisConfigOptions).isPresent()) {
      genesisConfigOptions
          .getBlobScheduleOptions()
          .flatMap(blobGetter)
          .ifPresent(builder::blobSchedule);
    }
    return builder.hardforkId(hardforkId);
  }

  static ProtocolSpecBuilder futureSipsDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return amsterdamDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .precompileContractRegistryBuilder(SilaMainnetPrecompiledContractRegistries::futureSips)
        .hardforkId(FUTURE_SIPS);
  }

  static ProtocolSpecBuilder experimentalSipsDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final SavmConfiguration savmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {

    return futureSipsDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            savmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .savmBuilder(
            (gasCalculator, jdCacheConfig) ->
                SilaMainnetSAVMs.experimentalSips(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), savmConfiguration))
        .hardforkId(EXPERIMENTAL_SIPS);
  }

  private static class FrontierTransactionReceiptFactory implements TransactionReceiptFactory {

    @Override
    public TransactionReceipt create(
        final TransactionType transactionType,
        final TransactionProcessingResult result,
        final WorldState worldState,
        final long gasUsed) {
      return new TransactionReceipt(
          worldState.frontierRootHash(),
          gasUsed,
          result.getLogs(),
          Optional.empty()); // No revert reason in Frontier
    }

    @Override
    public TransactionReceipt create(
        final TransactionType transactionType,
        final TransactionProcessingResult result,
        final long gasUsed) {
      throw new UnsupportedOperationException("No stateless transaction receipt in Frontier");
    }
  }

  private abstract static class PostFrontierTransactionReceiptFactory
      implements TransactionReceiptFactory {
    protected final boolean revertReasonEnabled;

    public PostFrontierTransactionReceiptFactory(final boolean revertReasonEnabled) {
      this.revertReasonEnabled = revertReasonEnabled;
    }

    @Override
    public TransactionReceipt create(
        final TransactionType transactionType,
        final TransactionProcessingResult result,
        final WorldState worldState,
        final long gasUsed) {
      return create(transactionType, result, gasUsed);
    }
  }

  static class ByzantiumTransactionReceiptFactory extends PostFrontierTransactionReceiptFactory {
    public ByzantiumTransactionReceiptFactory(final boolean revertReasonEnabled) {
      super(revertReasonEnabled);
    }

    @Override
    public TransactionReceipt create(
        final TransactionType transactionType,
        final TransactionProcessingResult result,
        final long gasUsed) {
      return new TransactionReceipt(
          result.isSuccessful() ? 1 : 0,
          gasUsed,
          result.getLogs(),
          revertReasonEnabled ? result.getRevertReason() : Optional.empty());
    }
  }

  static class BerlinTransactionReceiptFactory extends PostFrontierTransactionReceiptFactory {

    public BerlinTransactionReceiptFactory(final boolean revertReasonEnabled) {
      super(revertReasonEnabled);
    }

    @Override
    public TransactionReceipt create(
        final TransactionType transactionType,
        final TransactionProcessingResult result,
        final long gasUsed) {
      return new TransactionReceipt(
          transactionType,
          result.isSuccessful() ? 1 : 0,
          gasUsed,
          result.getLogs(),
          revertReasonEnabled ? result.getRevertReason() : Optional.empty());
    }
  }

  private record DaoBlockProcessor(BlockProcessor wrapped) implements BlockProcessor {

    @Override
    public BlockProcessingResult processBlock(
        final ProtocolContext protocolContext,
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final Block block) {
      updateWorldStateForDao(worldState);
      return wrapped.processBlock(
          protocolContext,
          blockchain,
          worldState,
          block,
          new AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing());
    }

    @Override
    public BlockProcessingResult processBlock(
        final ProtocolContext protocolContext,
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final Block block,
        final Optional<BlockAccessList> blockAccessList) {
      updateWorldStateForDao(worldState);
      return wrapped.processBlock(protocolContext, blockchain, worldState, block, blockAccessList);
    }

    @Override
    public BlockProcessingResult processBlock(
        final ProtocolContext protocolContext,
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final Block block,
        final AbstractBlockProcessor.PreprocessingFunction preprocessingBlockFunction) {
      return processBlock(
          protocolContext,
          blockchain,
          worldState,
          block,
          Optional.empty(),
          preprocessingBlockFunction);
    }

    @Override
    public BlockProcessingResult processBlock(
        final ProtocolContext protocolContext,
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final Block block,
        final Optional<BlockAccessList> blockAccessList,
        final AbstractBlockProcessor.PreprocessingFunction preprocessingBlockFunction) {
      updateWorldStateForDao(worldState);
      return wrapped.processBlock(
          protocolContext,
          blockchain,
          worldState,
          block,
          blockAccessList,
          preprocessingBlockFunction);
    }

    private static final Address DAO_REFUND_CONTRACT_ADDRESS =
        Address.fromHexString("0xbf4ed7b27f1d666546e30d74d50d173d20bca754");

    private void updateWorldStateForDao(final MutableWorldState worldState) {
      try {
        final JsonArray json =
            new JsonArray(
                Resources.toString(
                    Objects.requireNonNull(this.getClass().getResource("/daoAddresses.json")),
                    StandardCharsets.UTF_8));
        final List<Address> addresses =
            IntStream.range(0, json.size())
                .mapToObj(json::getString)
                .map(Address::fromHexString)
                .toList();
        final WorldUpdater worldUpdater = worldState.updater();
        final MutableAccount daoRefundContract =
            worldUpdater.getOrCreate(DAO_REFUND_CONTRACT_ADDRESS);
        for (final Address address : addresses) {
          final MutableAccount account = worldUpdater.getOrCreate(address);
          final Wei balance = account.getBalance();
          account.decrementBalance(balance);
          daoRefundContract.incrementBalance(balance);
        }
        worldUpdater.commit();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  static ProtocolSpecBuilder.FeeMarketBuilder createFeeMarket(
      final long londonForkBlockNumber,
      final boolean isZeroBaseFee,
      final boolean isFixedBaseFee,
      final boolean supportsBlobs,
      final Wei minTransactionGasPrice,
      final ProtocolSpecBuilder.FeeMarketBuilder feeMarketBuilder) {
    if (isZeroBaseFee) {
      var baseFeeMarket =
          supportsBlobs
              ? FeeMarket.zeroBlobFee(londonForkBlockNumber)
              : FeeMarket.zeroBaseFee(londonForkBlockNumber);
      return blobSchedule -> baseFeeMarket;
    }
    if (isFixedBaseFee) {
      return blobSchedule -> FeeMarket.fixedBaseFee(londonForkBlockNumber, minTransactionGasPrice);
    }
    return feeMarketBuilder;
  }
}
