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
package org.hyperledger.besu.sila.silaMainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.ExecutionContextTestFixture;
import org.hyperledger.besu.sila.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * SIP-7685: a SilaPrague block whose RLP header omits the mandatory {@code requestsHash} field must
 * be rejected by full block import rather than accepted as chain head.
 */
class RequestsHashMissingBlockImportTest {

  private static final String GENESIS_RESOURCE =
      "/org/hyperledger/besu/sila/sila-mainnet/genesis-prague-missing-requests-hash.json";

  // 575-byte raw block RLP for a SilaPrague block with an empty body and no requestsHash header
  // field.
  private static final String RAW_BLOCK_RLP_HEX =
      "0xf9023cf90236a03d1cd8c35772cc88dfe5c96faf999b9497d19e482134fd1ef52f80c0b44ec4bca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347940000000000000000000000000000000000000000a00f9cfbdcb99570c0d0d3a4ca1f5b827c5f9e2f8a057d54b0602299f27c7e5195a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000080018401c9c380800180a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a47088000000000000000007a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b4218080a00000000000000000000000000000000000000000000000000000000000004788c0c0c0";

  private static final String EXPECTED_BLOCK_HASH =
      "0x9e7900131c0cf6634b5645324f324bb43e06325635ed33ce42599083ce100a72";

  @Test
  void rejectsPragueBlockMissingRequestsHash() {
    final ExecutionContextTestFixture fixture =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(GENESIS_RESOURCE))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();

    final Block block =
        Block.readFrom(
            RLP.input(Bytes.fromHexString(RAW_BLOCK_RLP_HEX)),
            new SilaMainnetBlockHeaderFunctions());

    // Sanity: the requestsHash field really is absent from this block.
    assertThat(block.getHeader().getRequestsHash()).isEmpty();
    assertThat(block.getHash().toHexString()).isEqualTo(EXPECTED_BLOCK_HASH);

    final BlockImportResult importResult =
        fixture
            .getProtocolSchedule()
            .getByBlockHeader(block.getHeader())
            .getBlockImporter()
            .importBlock(
                fixture.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.FULL);

    assertThat(importResult.isImported()).isFalse();
    assertThat(fixture.getBlockchain().contains(block.getHash())).isFalse();
  }

  @Test
  void rejectsPragueBlockMissingRequestsHashUnderLightValidation() {
    // The SNAP/RLPx sync pivot uses light header validation. The presence rule opts into light
    // validation so this path is covered too.
    final ExecutionContextTestFixture fixture =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(GENESIS_RESOURCE))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();

    final Block block =
        Block.readFrom(
            RLP.input(Bytes.fromHexString(RAW_BLOCK_RLP_HEX)),
            new SilaMainnetBlockHeaderFunctions());

    final BlockImportResult importResult =
        fixture
            .getProtocolSchedule()
            .getByBlockHeader(block.getHeader())
            .getBlockImporter()
            .importBlock(
                fixture.getProtocolContext(),
                block,
                HeaderValidationMode.LIGHT,
                HeaderValidationMode.LIGHT);

    assertThat(importResult.isImported()).isFalse();
    assertThat(fixture.getBlockchain().contains(block.getHash())).isFalse();
  }
}
