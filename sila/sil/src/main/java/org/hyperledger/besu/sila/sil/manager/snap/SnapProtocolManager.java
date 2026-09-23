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

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapProtocolManager implements ProtocolManager {
  private static final Logger LOG = LoggerFactory.getLogger(SnapProtocolManager.class);

  private final List<Capability> supportedCapabilities;
  private final SilPeers silPeers;
  private final SilMessages snapMessages;
  private final SilScheduler silScheduler;

  public SnapProtocolManager(
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final SnapSyncConfiguration snapConfig,
      final SilPeers silPeers,
      final SilMessages snapMessages,
      final SilScheduler silScheduler,
      final ProtocolContext protocolContext,
      final Synchronizer synchronizer) {
    this.silPeers = silPeers;
    this.snapMessages = snapMessages;
    this.silScheduler = silScheduler;
    this.supportedCapabilities = calculateCapabilities(snapConfig);
    new SnapServer(
        snapConfig, snapMessages, worldStateStorageCoordinator, protocolContext, synchronizer);
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
    final SilMessage decodedSilMessage = new SilMessage(silPeer, messageData);

    // Dispatch to pending response handlers (no-op for inbound requests).
    silPeers.dispatchMessage(silPeer, decodedSilMessage, getSupportedProtocol());

    // GET_* requests are handled off the Netty event loop to avoid blocking SIL protocol traffic.
    if (SnapV1.REQUEST_CODES.contains(code) || SnapV2.REQUEST_CODES.contains(code)) {
      scheduleSnapRequest(silPeer, decodedSilMessage, cap, code);
    }
  }

  private void scheduleSnapRequest(
      final SilPeer silPeer,
      final SilMessage decodedSilMessage,
      final Capability cap,
      final int code) {
    silScheduler
        .scheduleServiceTask(
            () -> {
              Optional<MessageData> maybeResponseData = Optional.empty();
              try {
                final Map.Entry<BigInteger, MessageData> requestIdAndSilMessage =
                    decodedSilMessage.getData().unwrapMessageData();
                maybeResponseData =
                    snapMessages
                        .dispatch(new SilMessage(silPeer, requestIdAndSilMessage.getValue()), cap)
                        .map(
                            responseData ->
                                responseData.wrapMessageData(requestIdAndSilMessage.getKey()));
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
            });
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
      final boolean initiatedByPeer) {}

  @Override
  public int getHighestProtocolVersion() {
    return getSupportedCapabilities().stream()
        .max(Comparator.comparing(Capability::getVersion))
        .map(Capability::getVersion)
        .orElse(0);
  }
}
