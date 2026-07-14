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
package org.hyperledger.besu.sila.sil.sync;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.sila.sil.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.sila.sil.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.sila.sil.sync.range.RangeHeaders;
import org.hyperledger.besu.sila.sil.sync.range.SyncTargetRange;
import org.hyperledger.besu.sila.sil.sync.tasks.DownloadHeaderSequenceTask;
import org.hyperledger.besu.sila.sila-mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.FutureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadHeadersStep
    implements Function<SyncTargetRange, CompletableFuture<RangeHeaders>> {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadHeadersStep.class);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final SilContext silContext;
  private final ValidationPolicy validationPolicy;
  private final int headerRequestSize;
  private final MetricsSystem metricsSystem;

  public DownloadHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final SilContext silContext,
      final ValidationPolicy validationPolicy,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.silContext = silContext;
    this.validationPolicy = validationPolicy;
    this.headerRequestSize = headerRequestSize;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<RangeHeaders> apply(final SyncTargetRange checkpointRange) {
    final CompletableFuture<List<BlockHeader>> taskFuture = downloadHeaders(checkpointRange);
    final CompletableFuture<RangeHeaders> processedFuture =
        taskFuture.thenApply(headers -> processHeaders(checkpointRange, headers));
    FutureUtils.propagateCancellation(processedFuture, taskFuture);
    return processedFuture;
  }

  private CompletableFuture<List<BlockHeader>> downloadHeaders(final SyncTargetRange range) {
    if (range.hasEnd()) {
      LOG.debug(
          "Downloading headers for range {} to {}",
          range.getStart().getNumber(),
          range.getEnd().getNumber());
      if (range.getSegmentLengthExclusive() == 0) {
        // There are no extra headers to download.
        return completedFuture(emptyList());
      }
      return DownloadHeaderSequenceTask.endingAtHeader(
              protocolSchedule,
              protocolContext,
              silContext,
              range.getEnd(),
              range.getSegmentLengthExclusive(),
              validationPolicy,
              metricsSystem)
          .run();
    } else {
      LOG.debug("Downloading headers starting from {}", range.getStart().getNumber());
      return silContext
          .getScheduler()
          .scheduleServiceTask(
              () -> {
                GetHeadersFromPeerTask task =
                    new GetHeadersFromPeerTask(
                        range.getStart().getHash(),
                        range.getStart().getNumber(),
                        headerRequestSize,
                        0,
                        GetHeadersFromPeerTask.Direction.FORWARD,
                        protocolSchedule);
                PeerTaskExecutorResult<List<BlockHeader>> taskResult =
                    silContext.getPeerTaskExecutor().execute(task);
                if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
                    || taskResult.result().isEmpty()) {
                  return CompletableFuture.failedFuture(
                      new RuntimeException("Unable to download headers for range " + range));
                }
                return CompletableFuture.completedFuture(taskResult.result().get());
              });
    }
  }

  private RangeHeaders processHeaders(
      final SyncTargetRange checkpointRange, final List<BlockHeader> headers) {
    if (checkpointRange.hasEnd()) {
      final List<BlockHeader> headersToImport = new ArrayList<>(headers);
      headersToImport.add(checkpointRange.getEnd());
      return new RangeHeaders(checkpointRange, headersToImport);
    } else {
      List<BlockHeader> headersToImport = headers;
      if (!headers.isEmpty() && headers.getFirst().equals(checkpointRange.getStart())) {
        headersToImport = headers.subList(1, headers.size());
      }
      return new RangeHeaders(checkpointRange, headersToImport);
    }
  }
}
