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
package org.hyperledger.besu.sila.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.hyperledger.besu.consensus.merge.blockcreation.ReferenceTestMergeBlockCreator;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.BlockProcessingResult;
import org.hyperledger.besu.sila.BlockValidator;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.MiningConfiguration;
import org.hyperledger.besu.sila.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.core.Withdrawal;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskRequestSender;
import org.hyperledger.besu.sila.sil.sync.SyncMode;
import org.hyperledger.besu.sila.sil.sync.state.SyncState;
import org.hyperledger.besu.sila.sil.transactions.BlobCache;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPoolFactory;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.silaMainnet.HeaderValidationMode;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSpec;
import org.hyperledger.besu.sila.referencetests.BlockchainReferenceTestCaseSpec;
import org.hyperledger.besu.sila.referencetests.BlockExceptionMatcher;
import org.hyperledger.besu.sila.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.sila.rlp.RLPException;
import org.hyperledger.besu.sila.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;
import org.hyperledger.besu.savm.SAVM;
import org.hyperledger.besu.savm.SavmSpecVersion;
import org.hyperledger.besu.savm.account.AccountState;
import org.hyperledger.besu.savm.internal.SavmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

public class BlockchainReferenceTestTools {

    private static final List<String> NETWORKS_TO_RUN;
    private static final ReferenceTestProtocolSchedules PROTOCOL_SCHEDULES;

    static {
        final String networks =
                System.getProperty(
                        "test.sila.blockchain.sips",
                        "FrontierToHomesteadAt5,HomesteadToSIP150At5,HomesteadToDaoAt5,SIP158ToByzantiumAt5,SilaCancunToSilaPragueAtTime15k,"
                                + "Frontier,Homestead,SIP150,SIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul,Berlin,"
                                + "London,Merge,SilaParis,SilaShanghai,SilaCancun,SilaPrague,SilaOsaka,SilaAmsterdam,Bogota,Polis,Bangkok");
        NETWORKS_TO_RUN = Arrays.asList(networks.split(","));
        PROTOCOL_SCHEDULES = ReferenceTestProtocolSchedules.create();
    }

    private static final JsonTestParameters<?, ?> params =
            JsonTestParameters.create(BlockchainReferenceTestCaseSpec.class)
                    .generator(
                            (testName, fullPath, spec, collector) -> {
                                final String sip = spec.getNetwork();
                                collector.add(
                                        testName + "[" + sip + "]", fullPath, spec, NETWORKS_TO_RUN.contains(sip));
                            });

    static {
        if (NETWORKS_TO_RUN.isEmpty()) {
            params.ignoreAll();
        }

        // Consumes a huge amount of memory
        params.ignore("static_Call1MB1024Calldepth");
        params.ignore("SilaShanghaiLove_");

        // Absurd amount of gas, doesn't run in parallel
        params.ignore("randomStatetest94_\\w+");

        // Don't do time-consuming tests
        params.ignore("CALLBlake2f_MaxRounds");
        params.ignore("loopMul_");

        // Inconclusive fork choice rule, since in merge CL should be choosing forks and setting the
        // chain head.
        // Perfectly valid test pre-merge.
        params.ignore(
                "UncleFromSideChain_(Merge|SilaParis|SilaShanghai|SilaCancun|SilaPrague|SilaOsaka|SilaAmsterdam|Bogota|Polis|Bangkok)");

        // These are for the older reference tests but SIP-2537 is covered by sip2537_bls_12_381_precompiles in the execution-spec-tests
        params.ignore("/stSIP2537/");
    }

    private BlockchainReferenceTestTools() {
        // utility class
    }

    public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
        return params.generate(filePath);
    }

    @SuppressWarnings("java:S5960") // this is actually test code
    public static void executeTest(final String name, final BlockchainReferenceTestCaseSpec spec) {
      final MutableBlockchain blockchain = spec.buildBlockchain();
      final BlockHeader genesisBlockHeader = spec.getGenesisBlockHeader();
        final ProtocolContext protocolContext = spec.buildProtocolContext(DataStorageConfiguration.DEFAULT_BONSAI_CONFIG, blockchain);
        final WorldStateArchive worldStateArchive = protocolContext.getWorldStateArchive();
        final MutableWorldState worldState =
                worldStateArchive
                        .getWorldState(WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(genesisBlockHeader))
                        .orElseThrow();

        final ProtocolSchedule schedule = PROTOCOL_SCHEDULES.getByName(spec.getNetwork());

        try (BlockCreationFixture blockCreation =
                     BlockCreationFixture.create(schedule, protocolContext, blockchain)) {
            for (final BlockchainReferenceTestCaseSpec.CandidateBlock candidateBlock :
                    spec.getCandidateBlocks()) {
                if (!candidateBlock.isExecutable()) {
                    return;
                }

                try {
                    final Block blockFromReference = candidateBlock.getBlock();

                    final ProtocolSpec protocolSpec = schedule.getByBlockHeader(blockFromReference.getHeader());

                    verifyJournaledSAVMAccountCompatability(worldState, protocolSpec);

                    final boolean supportsBlockBuilding =
                            ReferenceTestProtocolSchedules.supportsBlockBuilding(spec.getNetwork());
                    final boolean shouldBuildBlock = supportsBlockBuilding && candidateBlock.isValid() && !name.contains("sip7934");
                    final Block block =
                            shouldBuildBlock
                                    ? buildBlock(
                                    schedule,
                                    protocolContext,
                                    blockchain,
                                    blockCreation.transactionPool(),
                                    blockCreation.silScheduler(),
                                    blockFromReference)
                                    : blockFromReference;

                    assertThat(block).isEqualTo(blockFromReference);

                    final HeaderValidationMode validationMode =
                            "NoProof".equalsIgnoreCase(spec.getSealEngine())
                                    ? HeaderValidationMode.LIGHT
                                    : HeaderValidationMode.FULL;

                    // Use validateAndProcessBlock directly so we can access the error message and
                    // verify it matches the expected exception from the fixture.
                    final BlockValidator blockValidator = protocolSpec.getBlockValidator();
                    final BlockProcessingResult processingResult =
                            blockValidator.validateAndProcessBlock(
                                    protocolContext,
                                    block,
                                    validationMode,
                                    validationMode,
                                    candidateBlock.getBlockAccessList(),
                                    false);

                    final boolean imported = processingResult.isSuccessful();
                    if (imported) {
                        // Block was accepted: persist and append it just like SilaMainnetBlockImporter.
                        processingResult.getYield().ifPresent(outputs -> {
                            protocolContext.getBlockchain().appendBlock(block, outputs.getReceipts(), outputs.getBlockAccessList());
                            protocolContext.getWorldStateArchive().getWorldState(
                                    WorldStateQueryParams.newBuilder()
                                            .withBlockHeader(block.getHeader())
                                            .withShouldWorldStateUpdateHead(true)
                                            .build());
                        });
                    }

                    assertThat(imported)
                            .as("Block import status for block %s", block.getHash())
                            .isEqualTo(candidateBlock.isValid());

                    // When the block is expected to be invalid, verify the rejection reason matches
                    // the expected exception from the fixture.
                    if (!candidateBlock.isValid()) {
                        candidateBlock.getExpectedException().ifPresent(expectedExceptionKey -> {
                            final String actualError = processingResult.errorMessage.orElse("");
                            assertThat(BlockExceptionMatcher.matches(expectedExceptionKey, actualError))
                                    .as(
                                            "Block rejected for wrong reason.\n"
                                                    + "  Expected exception : %s (%s)\n"
                                                    + "  Actual error       : %s",
                                            expectedExceptionKey,
                                            BlockExceptionMatcher.describeExpected(expectedExceptionKey).orElse("unknown key"),
                                            actualError)
                                    .isTrue();
                        });
                    }

                } catch (final RLPException e) {
                    assertThat(candidateBlock.isValid()).isFalse();
                }
            }
        }

        assertThat(blockchain.getChainHeadHash()).isEqualTo(spec.getLastBlockHash());

  }

  private static Block buildBlock(
      final ProtocolSchedule schedule,
      final ProtocolContext context,
      final MutableBlockchain blockchain,
      final TransactionPool transactionPool,
      final SilScheduler silScheduler,
      final Block blockFromReference) {

    final MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();
    miningConfiguration.setMiningEnabled(true);
    miningConfiguration.setCoinbase(blockFromReference.getHeader().getCoinbase());
    miningConfiguration.setExtraData(blockFromReference.getHeader().getExtraData());
    miningConfiguration.setMinTransactionGasPrice(Wei.ZERO);
    miningConfiguration.setMinPriorityFeePerGas(Wei.ZERO);
    miningConfiguration.setTargetGasLimit(blockFromReference.getHeader().getGasLimit());

    final List<Transaction> transactions = blockFromReference.getBody().getTransactions();
    final Optional<List<Withdrawal>> withdrawals = blockFromReference.getBody().getWithdrawals();
    final List<BlockHeader> ommers = blockFromReference.getBody().getOmmers();
    final BlockHeader parentHeader =
        blockchain
            .getBlockHeader(blockFromReference.getHeader().getParentHash())
            .orElseThrow();
    return ReferenceTestMergeBlockCreator.createBlock(
        miningConfiguration,
        parent -> blockFromReference.getHeader().getExtraData(),
        transactionPool,
        context,
        schedule,
        parentHeader,
        silScheduler,
        Optional.of(transactions),
        Optional.of(ommers),
        blockFromReference.getHeader().getMixHashOrPrevRandao(),
        blockFromReference.getHeader().getTimestamp(),
        withdrawals,
        blockFromReference.getHeader().getParentBeaconBlockRoot(),
        blockFromReference.getHeader().getOptionalSlotNumber());
  }

  static void verifyJournaledSAVMAccountCompatability(
          final MutableWorldState worldState, final ProtocolSpec protocolSpec) {
    SAVM savm = protocolSpec.getSavm();
    if (savm.getSavmConfiguration().worldUpdaterMode() == WorldUpdaterMode.JOURNALED) {
      assumeFalse(
              worldState
                      .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE).anyMatch(AccountState::isEmpty),
              "Journaled account configured and empty account detected");
      assumeFalse(SavmSpecVersion.SPURIOUS_DRAGON.compareTo(savm.getSavmVersion()) > 0,
              "Journaled account configured and fork prior to the merge specified");
    }
  }

  private static final class BlockCreationFixture implements AutoCloseable {
    private final SilScheduler silScheduler;
    private final TransactionPool transactionPool;
    private final MutableBlockchain blockchain;

    private BlockCreationFixture(
        final SilScheduler silScheduler,
        final TransactionPool transactionPool,
        final MutableBlockchain blockchain) {
      this.silScheduler = silScheduler;
      this.transactionPool = transactionPool;
      this.blockchain = blockchain;
    }

    static BlockCreationFixture create(
        final ProtocolSchedule schedule,
        final ProtocolContext context,
        final MutableBlockchain blockchain) {
      final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
      final SilScheduler silScheduler = new SilScheduler(1, 1, 1, metricsSystem);
      final SilPeers silPeers =
          new SilPeers(
              () -> schedule.getByBlockHeader(blockchain.getChainHeadHeader()),
              Clock.systemUTC(),
              metricsSystem,
              SilProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
              Collections.emptyList(),
              Bytes.random(SilPeers.NODE_ID_LENGTH),
              1,
              1,
              false,
              SyncMode.FULL,
              new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList()));
      final SilContext silContext =
          new SilContext(
              silPeers,
              new SilMessages(),
              silScheduler,
              new PeerTaskExecutor(silPeers, new PeerTaskRequestSender(), metricsSystem));
      final SyncState syncState = new SyncState(blockchain, silPeers);
      final TransactionPool transactionPool =
          TransactionPoolFactory.createTransactionPool(
              schedule,
              context,
              silContext,
              Clock.systemUTC(),
              metricsSystem,
              syncState,
              TransactionPoolConfiguration.DEFAULT,
              SilProtocolConfiguration.DEFAULT,
              new BlobCache(),
              MiningConfiguration.newDefault());

      return new BlockCreationFixture(silScheduler, transactionPool, blockchain);
    }

    TransactionPool transactionPool() {
      return transactionPool;
    }

    SilScheduler silScheduler() {
      return silScheduler;
    }

    @Override
    public void close() {
      silScheduler.stop();
      blockchain.removeAllBlockAddedObservers();
    }
  }
}
