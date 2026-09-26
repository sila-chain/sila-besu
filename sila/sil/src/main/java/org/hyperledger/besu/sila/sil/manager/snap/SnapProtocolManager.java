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
package org.hyperledger.besu.sila.sil.manager.snap;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.sila.ProtocolContext;
import org.hyperledger.besu.sila.core.Synchronizer;
import org.hyperledger.besu.sila.p2p.network.ProtocolManager;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.framing.FramingException;
import org.hyperledger.besu.sila.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Message;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.rlp.RLPException;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.manager.SilMessage;
import org.hyperledger.besu.sila.sil.manager.SilMessages;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.sil.manager.SilPeers;
import org.hyperledger.besu.sila.sil.manager.SilScheduler;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV1;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV2;
import org.hyperledger.besu.sila.sil.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.sila.worldstate.WorldStateStorageCoordinator;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapProtocolManager implements ProtocolManager {
  private static final Logger LOG = LoggerFactory.getLogger(SnapProtocolManager.class);

  private final List<Capability> supportedCapabilities;
  private final SilPeers silPeers;
  private final SilMessages snapMessages;
  private final SilScheduler silScheduler;

  private final int maxConcurrentRequestsPerPeer;
  private final int maxConcurrentRequestsGlobal;
  private final AtomicInteger globalInFlightRequests = new AtomicInteger(0);
  private final Map<PeerConnection, AtomicInteger> perPeerInFlightRequests =
      new ConcurrentHashMap<>();
  private final Counter rejectedRequestsCounter;

  public SnapProtocolManager(
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final SnapSyncConfiguration snapConfig,
      final SilPeers silPeers,
      final SilMessages snapMessages,
      final SilScheduler silScheduler,
      final ProtocolContext protocolContext,
      final Synchronizer synchronizer,
      final MetricsSystem metricsSystem) {
    this.silPeers = silPeers;
    this.snapMessages = snapMessages;
    this.silScheduler = silScheduler;
    this.supportedCapabilities = calculateCapabilities(snapConfig);
    this.maxConcurrentRequestsPerPeer = snapConfig.getMaxConcurrentSnapRequestsPerPeer();
    this.maxConcurrentRequestsGlobal = snapConfig.getMaxConcurrentSnapRequestsGlobal();
    new SnapServer(
        snapConfig, snapMessages, worldStateStorageCoordinator, protocolContext, synchronizer);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PEERS,
        "snap_service_requests_in_flight_current",
        "The current number of snap sync GET_* requests concurrently scheduled for processing",
        globalInFlightRequests::get);
    this.rejectedRequestsCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PEERS,
            "snap_service_requests_rejected_total",
            "Total number of snap sync GET_* requests answered with an empty response because a concurrency cap was reached");
  }

  private List<Capability> calculateCapabilities(final SnapSyncConfiguration snapConfig) {
    final ImmutableList.Builder<Capability> capabilities = ImmutableList.builder();
    capabilities.add(SnapProtocol.SNAP1);
    if (Boolean.TRUE.equals(snapConfig.isSnap2Enabled())) {
      capabilities.add(SnapProtocol.SNAP2);
    }

    return capabilities.build();
  }

  @Override
  public String getSupportedProtocol() {
    return SnapProtocol.NAME;
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  @Override
  public void stop() {}

  @Override
  public void awaitStop() throws InterruptedException {}

  /**
   * This function is called by the P2P framework when a SNAP message has been received.
   *
   * @param cap The capability under which the message was transmitted.
   * @param message The message to be decoded.
   */
  @Override
  public void processMessage(final Capability cap, final Message message) {
    final int code = message.getData().getCode();
    LOG.trace("Process snap message {}, {}", cap, code);
    final SilPeer silPeer = silPeers.peer(message.getConnection());
    if (silPeer == null) {
      LOG.debug(
          "Ignoring message received from unknown peer connection: {}", message.getConnection());
      return;
    }

    final SilMessage silMessage = new SilMessage(silPeer, message.getData());
    if (!silPeer.validateReceivedMessage(silMessage, getSupportedProtocol())) {
      LOG.debug("Unsolicited message {} received from, disconnecting: {}", code, silPeer);
      silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_UNSOLICITED_MESSAGE_RECEIVED);
      return;
    }

    // Decode the snap message. FramingException (decompression failure) is a protocol violation.
    final MessageData messageData;
    try {
      messageData = AbstractSnapMessageData.create(message);
    } catch (final FramingException e) {
      LOG.atDebug()
          .setMessage("Disconnecting peer {} due to decompression failure for message code {}")
          .addArgument(silPeer::getLoggableId)
          .addArgument(code)
          .setCause(e)
          .log();
      silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
      return;
    }
    final SilMessage decodedEthMessage = new SilMessage(silPeer, messageData);

    // Dispatch to pending response handlers (no-op for inbound requests).
    silPeers.dispatchMessage(silPeer, decodedEthMessage, getSupportedProtocol());

    // GET_* requests are handled off the Netty event loop to avoid blocking SIL protocol traffic.
    if (SnapV1.REQUEST_CODES.contains(code) || SnapV2.REQUEST_CODES.contains(code)) {
      scheduleSnapRequest(silPeer, decodedEthMessage, cap, code);
    }
  }

  private void scheduleSnapRequest(
      final SilPeer silPeer,
      final SilMessage decodedEthMessage,
      final Capability cap,
      final int code) {
    if (!reserveSnapRequestSlot(silPeer)) {
      respondEmptyDueToOverload(silPeer, decodedEthMessage, code);
      return;
    }
    try {
      scheduleReservedSnapRequest(silPeer, decodedEthMessage, cap, code);
    } catch (final RuntimeException e) {
      // Only reachable during shutdown: the services executor rejects synchronously once shut
      // down. Release the slot so it isn't leaked.
      releaseSnapRequestSlot(silPeer);
      throw e;
    }
  }

  /** Cap was hit; reply empty instead of leaving the peer to time out. */
  private void respondEmptyDueToOverload(
      final SilPeer silPeer, final SilMessage decodedEthMessage, final int code) {
    final BigInteger requestId;
    try {
      requestId = decodedEthMessage.getData().unwrapMessageData().getKey();
    } catch (final RLPException e) {
      LOG.debug(
          "Received malformed snap message code={} (BREACH_OF_PROTOCOL), disconnecting: {}",
          code,
          silPeer,
          e);
      silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
      return;
    }
    sendSnapResponse(silPeer, emptyResponseFor(code).wrapMessageData(requestId));
  }

  private static MessageData emptyResponseFor(final int code) {
    return switch (code) {
      case SnapV1.GET_ACCOUNT_RANGE -> SnapServer.EMPTY_ACCOUNT_RANGE;
      case SnapV1.GET_STORAGE_RANGE -> SnapServer.EMPTY_STORAGE_RANGE;
      case SnapV1.GET_BYTECODES -> SnapServer.EMPTY_BYTE_CODES_MESSAGE;
      case SnapV1.GET_TRIE_NODES -> SnapServer.EMPTY_TRIE_NODES_MESSAGE;
      case SnapV2.GET_BLOCK_ACCESS_LISTS -> SnapServer.EMPTY_BLOCK_ACCESS_LISTS;
      default -> throw new IllegalStateException("Unhandled snap GET_* code: " + code);
    };
  }

  private void scheduleReservedSnapRequest(
      final SilPeer silPeer,
      final SilMessage decodedEthMessage,
      final Capability cap,
      final int code) {
    silScheduler
        .scheduleServiceTask(
            () -> {
              Optional<MessageData> maybeResponseData = Optional.empty();
              try {
                final Map.Entry<BigInteger, MessageData> requestIdAndEthMessage =
                    decodedEthMessage.getData().unwrapMessageData();
                maybeResponseData =
                    snapMessages
                        .dispatch(new SilMessage(silPeer, requestIdAndEthMessage.getValue()), cap)
                        .map(
                            responseData ->
                                responseData.wrapMessageData(requestIdAndEthMessage.getKey()));
              } catch (final FramingException | RLPException e) {
                LOG.debug(
                    "Received malformed snap message code={} (BREACH_OF_PROTOCOL), disconnecting: {}",
                    code,
                    silPeer,
                    e);
                silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
              }
              maybeResponseData.ifPresent(responseData -> sendSnapResponse(silPeer, responseData));
            })
        .exceptionally(
            e -> {
              if (!(e instanceof CancellationException)) {
                LOG.atWarn()
                    .setMessage("Unexpected error handling snap request code={} from peer {}")
                    .addArgument(code)
                    .addArgument(silPeer::getLoggableId)
                    .setCause(e)
                    .log();
              }
              return null;
            })
        .whenComplete((result, error) -> releaseSnapRequestSlot(silPeer));
  }

  /**
   * Reserves a global and per-peer slot before scheduling; increment-then-check avoids a TOCTOU
   * race.
   *
   * @return true if reserved; false if a cap was hit (request answered empty instead).
   */
  private boolean reserveSnapRequestSlot(final SilPeer silPeer) {
    final int reservedGlobal = globalInFlightRequests.incrementAndGet();
    if (maxConcurrentRequestsGlobal > 0 && reservedGlobal > maxConcurrentRequestsGlobal) {
      globalInFlightRequests.decrementAndGet();
      rejectSnapRequest(silPeer, "global");
      return false;
    }
    if (maxConcurrentRequestsPerPeer > 0) {
      final AtomicInteger perPeerCount =
          perPeerInFlightRequests.computeIfAbsent(
              silPeer.getConnection(), unused -> new AtomicInteger(0));
      final int reservedPerPeer = perPeerCount.incrementAndGet();
      if (reservedPerPeer > maxConcurrentRequestsPerPeer) {
        perPeerCount.decrementAndGet();
        globalInFlightRequests.decrementAndGet();
        rejectSnapRequest(silPeer, "per-peer");
        return false;
      }
    }
    return true;
  }

  private void releaseSnapRequestSlot(final SilPeer silPeer) {
    globalInFlightRequests.decrementAndGet();
    if (maxConcurrentRequestsPerPeer > 0) {
      final AtomicInteger perPeerCount = perPeerInFlightRequests.get(silPeer.getConnection());
      if (perPeerCount != null) {
        perPeerCount.decrementAndGet();
      }
    }
  }

  private void rejectSnapRequest(final SilPeer silPeer, final String scope) {
    rejectedRequestsCounter.inc();
    LOG.atDebug()
        .setMessage("Answering snap request from peer {} empty: {} concurrency cap reached")
        .addArgument(silPeer::getLoggableId)
        .addArgument(scope)
        .log();
  }

  private void sendSnapResponse(final SilPeer silPeer, final MessageData responseData) {
    try {
      silPeer.send(responseData, getSupportedProtocol());
    } catch (final PeerConnection.PeerNotConnected e) {
      LOG.atTrace()
          .setMessage("Peer disconnected before we could respond - nothing to do {}")
          .addArgument(e.getMessage())
          .log();
    }
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {}

  @Override
  public void handleDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    perPeerInFlightRequests.remove(connection);
  }

  @Override
  public int getHighestProtocolVersion() {
    return getSupportedCapabilities().stream()
        .max(Comparator.comparing(Capability::getVersion))
        .map(Capability::getVersion)
        .orElse(0);
  }
}
