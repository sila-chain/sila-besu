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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.SoftAssertions;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.BytesHolder;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.BlockHeaderBuilder;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.sila-mainnet.SilaMainnetTransactionProcessor;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSpec;
import org.hyperledger.besu.sila.sila-mainnet.TransactionValidationParams;
import org.hyperledger.besu.sila.processing.TransactionProcessingResult;
import org.hyperledger.besu.sila.referencetests.GeneralStateTestCaseSipSpec;
import org.hyperledger.besu.sila.referencetests.GeneralStateTestCaseSpec;
import org.hyperledger.besu.sila.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.sila.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.sila.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.sila.rlp.RLP;
import org.hyperledger.besu.savm.account.Account;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.savm.worldstate.WorldUpdater;
import org.hyperledger.besu.testutil.JsonTestParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneralStateReferenceTestTools {
  private static final Logger LOG = LoggerFactory.getLogger(GeneralStateReferenceTestTools.class);

  private static SilaMainnetTransactionProcessor transactionProcessor(final String name) {
    return protocolSpec(name).getTransactionProcessor();
  }

  private static ProtocolSpec protocolSpec(final String name) {
    return PROTOCOL_SCHEDULES.getByName(name).getByBlockHeader(BlockHeaderBuilder.createDefault().buildBlockHeader());
  }

  private static final List<String> SIPS_TO_RUN;
  private static final ReferenceTestProtocolSchedules PROTOCOL_SCHEDULES;

  static {
    final String sips =
        System.getProperty(
            "test.sila.state.sips",
            "Frontier,Homestead,SIP150,SIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul,Berlin,"
                + "London,Merge,SilaParis,SilaShanghai,SilaCancun,SilaPrague,SilaOsaka,SilaAmsterdam,Bogota,Polis,Bangkok");
    SIPS_TO_RUN = Arrays.asList(sips.split(","));
    PROTOCOL_SCHEDULES = ReferenceTestProtocolSchedules.create();
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(GeneralStateTestCaseSpec.class, GeneralStateTestCaseSipSpec.class)
          .generator(
              (testName, fullPath, stateSpec, collector) -> {
                final String prefix = testName + "-";
                for (final Map.Entry<String, List<GeneralStateTestCaseSipSpec>> entry :
                    stateSpec.finalStateSpecs().entrySet()) {
                  final String sip = entry.getKey();
                  final boolean runTest = SIPS_TO_RUN.contains(sip);
                  final List<GeneralStateTestCaseSipSpec> sipSpecs = entry.getValue();
                  if (sipSpecs.size() == 1) {
                    collector.add(prefix + sip, fullPath, sipSpecs.get(0), runTest);
                  } else {
                    for (int i = 0; i < sipSpecs.size(); i++) {
                      collector.add(
                          prefix + sip + '[' + i + ']', fullPath, sipSpecs.get(i), runTest);
                    }
                  }
                }
              });

  static {
    if (SIPS_TO_RUN.isEmpty()) {
      params.ignoreAll();
    }

    // Consumes a huge amount of memory
    params.ignore("static_Call1MB1024Calldepth");
    params.ignore("SilaShanghaiLove_.*");

    // Don't do time-consuming tests
    params.ignore("CALLBlake2f_MaxRounds.*");
    params.ignore("loopMul-.*");

    // These are for the older reference tests but SIP-2537 is covered by sip2537_bls_12_381_precompiles in the execution-spec-tests
    params.ignore("/stSIP2537/");
  }

  private GeneralStateReferenceTestTools() {
    // utility class
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  @SuppressWarnings("java:S5960") // this is actually test support code, not production code
  public static void executeTest(final GeneralStateTestCaseSipSpec spec) {
    final BlockHeader blockHeader = spec.getBlockHeader();
    final ReferenceTestWorldState initialWorldState = spec.getInitialWorldState();
    final Transaction transaction = spec.getTransaction(0);
    ProtocolSpec protocolSpec = protocolSpec(spec.getFork());

    BlockchainReferenceTestTools.verifyJournaledSAVMAccountCompatability(initialWorldState, protocolSpec);

    // Sometimes the tests ask us assemble an invalid transaction.  If we have
    // no valid transaction then there is no test.  GeneralBlockChain tests
    // will handle the case where we receive the TXs in a serialized form.
    if (transaction == null) {
      assertThat(spec.getExpectException())
          .withFailMessage("Transaction was not assembled, but no exception was expected")
          .isNotNull();
      return;
    }

    final ReferenceTestWorldState worldState = initialWorldState.copy();
    // Several of the GeneralStateTests check if the transaction could potentially
    // consume more gas than is left for the block it's attempted to be included in.
    // This check is performed within the `BlockImporter` rather than inside the
    // `TransactionProcessor`, so these tests are skipped.
    if (transaction.getGasLimit() > blockHeader.getGasLimit() - blockHeader.getGasUsed()) {
      return;
    }

    final SilaMainnetTransactionProcessor processor = transactionProcessor(spec.getFork());
    final WorldUpdater worldStateUpdater = worldState.updater();
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());
    final Wei blobGasPrice =
        protocolSpec
            .getFeeMarket()
            .blobGasPricePerGas(blockHeader.getExcessBlobGas().orElse(BlobGas.ZERO));
    final TransactionProcessingResult result =
        processor.processTransaction(
            worldStateUpdater,
            blockHeader,
            transaction,
            blockHeader.getCoinbase(),
            protocolSpec.getPreExecutionProcessor().createBlockHashLookup(blockchain, blockHeader),
            TransactionValidationParams.processingBlock(),
            blobGasPrice);
    if (result.isInvalid()) {
      assertThat(spec.getExpectException())
          .withFailMessage(() -> result.getValidationResult().getErrorMessage())
          .isNotNull();
      return;
    }
    assertThat(spec.getExpectException())
        .withFailMessage("Exception was expected - " + spec.getExpectException())
        .isNull();

    final Account coinbase = worldStateUpdater.getOrCreate(spec.getBlockHeader().getCoinbase());
    if (coinbase != null && coinbase.isEmpty() && ReferenceTestProtocolSchedules.shouldClearEmptyAccounts(spec.getFork())) {
      worldStateUpdater.deleteAccount(coinbase.getAddress());
    }
    worldStateUpdater.commit();
    Collection<Exception> additionalExceptions = worldState.processExtraStateStorageFormatValidation(blockHeader);
    worldState.persist(blockHeader);

    // Check the world state root hash.
    final Hash expectedRootHash = spec.getExpectedRootHash();
    // If the root hash doesn't match, first dump the world state for debugging.
    if (!expectedRootHash.equals(worldState.rootHash())) {
      logWorldState(worldState);
    }
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(worldState.rootHash())
              .withFailMessage(
                  "Unexpected world state root hash; expected state: %s, computed state: %s",
                  spec.getExpectedRootHash(), worldState.rootHash())
              .isEqualTo(expectedRootHash);
          additionalExceptions.forEach(
              e -> softly.fail("Additional exception during state validation: " + e.getMessage()));

        });

    // Check the logs.
    final Hash expectedLogsHash = spec.getExpectedLogsHash();
    Optional.ofNullable(expectedLogsHash)
        .ifPresent(
            expected -> {
              final List<Log> logs = result.getLogs();

              assertThat(Hash.hash(RLP.encode(out -> out.writeList(logs, Log::writeTo))))
                  .withFailMessage("Unmatched logs hash. Generated logs: %s", logs)
                  .isEqualTo(expected);
            });
  }

  private static void logWorldState(final ReferenceTestWorldState worldState) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode worldStateJson = mapper.createObjectNode();
    worldState.streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
        .forEach(
            account -> {
              ObjectNode accountJson = mapper.createObjectNode();
              accountJson.put("nonce", Bytes.ofUnsignedLong(account.getNonce()).toShortHexString());
              accountJson.put("balance", account.getBalance().toShortHexString());
              accountJson.put("code", account.getCode().toHexString());
              ObjectNode storageJson = mapper.createObjectNode();
              var storageEntries = account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
              storageEntries.values().stream()
                  .map(
                      e ->
                          Map.entry(
                              e.getKey().orElse(UInt256.fromBytes(Bytes.EMPTY)),
                              account.getStorageValue(UInt256.fromBytes(e.getKey().get()))))
                  .sorted(Map.Entry.comparingByKey())
                  .forEach(e -> storageJson.put(e.getKey().toQuantityHexString(), e.getValue().toQuantityHexString()));

              if (!storageEntries.isEmpty()) {
                accountJson.set("storage", storageJson);
              }
              worldStateJson.set(account.getAddress().map(BytesHolder::getBytes).map(Bytes::toHexString).orElse(Bytes.EMPTY.toHexString()), accountJson);
            });
    LOG.error("Calculated world state: \n{}", worldStateJson.toPrettyString());
  }
}
