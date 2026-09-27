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
package org.hyperledger.besu.sila.sil.sync.fullsync.era1prepipeline;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.chain.MutableBlockchain;
import org.hyperledger.besu.sila.core.BlockchainSetupUtil;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManager;
import org.hyperledger.besu.sila.sil.manager.SilProtocolManagerTestBuilder;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Era1ImportPrepipelineFactoryTest {
  private static URI testFileUri;

  protected SilProtocolManager silProtocolManager;
  protected SilContext silContext;

  protected MutableBlockchain localBlockchain;

  private Era1ImportPrepipelineFactory era1ImportPrepipelineFactory;

  @BeforeAll
  public static void setupClass() throws URISyntaxException {
    testFileUri =
        Path.of(
                Era1FileSourceTest.class
                    .getClassLoader()
                    .getResource("mainnet-00000-5ec1ffb8.era1")
                    .toURI())
            .getParent()
            .toUri();
  }

  @BeforeEach
  public void setupTest() {
    BlockchainSetupUtil localBlockchainSetup = BlockchainSetupUtil.forMainnet();
    localBlockchain = localBlockchainSetup.getBlockchain();

    ProtocolSchedule protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    ProtocolContext protocolContext = localBlockchainSetup.getProtocolContext();
    silProtocolManager =
        SilProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(localBlockchain)
            .setEthScheduler(new SilScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
            .setWorldStateArchive(localBlockchainSetup.getWorldArchive())
            .setTransactionPool(localBlockchainSetup.getTransactionPool())
            .setEthereumWireProtocolConfiguration(SilProtocolConfiguration.DEFAULT)
            .build();
    silContext = silProtocolManager.silContext();
    MetricsSystem metricsSystem = new NoOpMetricsSystem();

    era1ImportPrepipelineFactory =
        new Era1ImportPrepipelineFactory(
            metricsSystem,
            testFileUri,
            4,
            protocolSchedule,
            protocolContext,
            silContext,
            SyncTerminationCondition.never());
  }

  @AfterEach
  public void tearDown() {
    if (silProtocolManager != null) {
      silProtocolManager.stop();
    }
  }

  @Test
  public void test() throws ExecutionException, InterruptedException, TimeoutException {
    Pipeline<URI> pipeline =
        era1ImportPrepipelineFactory.createFileImportPipelineForCurrentBlockNumber(0);
    CompletableFuture<Void> pipelineFuture = silContext.getScheduler().startPipeline(pipeline);
    pipelineFuture.get();

    Assertions.assertEquals(8191, localBlockchain.getChainHeadBlockNumber());
  }
}
