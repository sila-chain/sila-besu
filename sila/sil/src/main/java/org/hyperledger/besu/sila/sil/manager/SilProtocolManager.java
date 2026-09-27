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
package org.hyperledger.besu.sila.sil.manager;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.chain.MinedBlockObserver;
import org.hyperledger.besu.sila.core.Block;
import org.hyperledger.besu.sila.core.Difficulty;
import org.hyperledger.besu.sila.forkid.ForkId;
import org.hyperledger.besu.sila.forkid.ForkIdManager;
import org.hyperledger.besu.sila.p2p.network.ProtocolManager;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.sila.p2p.rlpx.framing.FramingException;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Message;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.sila.rlp.RLPException;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.exceptions.ProtocolViolationException;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.messages.StatusMessage;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidator;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidatorRunner;
import org.hyperledger.besu.sila.sil.sync.BlockBroadcaster;
import org.hyperledger.besu.sila.sil.sync.BlockRangeBroadcaster;
import org.hyperledger.besu.sila.sil.sync.SynchronizerConfiguration;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilProtocolManager implements ProtocolManager, MinedBlockObserver {
  private static final Logger LOG = LoggerFactory.getLogger(SilProtocolManager.class);

  private final SilScheduler scheduler;
  private final CountDownLatch shutdown;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Hash genesisHash;
  private final ForkIdManager forkIdManager;
  private final BigInteger networkId;
  private final SilPeers silPeers;
  private final SilMessages silMessages;
  private final SilContext silContext;
  private final List<Capability> supportedCapabilities;
  private final Blockchain blockchain;
  private final BlockBroadcaster blockBroadcaster;
  private final List<PeerValidator> peerValidators;
  private final Optional<MergePeerFilter> mergePeerFilter;

  public SilProtocolManager(
      final Blockchain blockchain,
      final BigInteger networkId,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final SilProtocolConfiguration ethereumWireProtocolConfiguration,
      final SilPeers silPeers,
      final SilMessages silMessages,
      final SilContext silContext,
      final List<PeerValidator> peerValidators,
      final Optional<MergePeerFilter> mergePeerFilter,
      final SynchronizerConfiguration synchronizerConfiguration,
      final SilScheduler scheduler,
      final ForkIdManager forkIdManager) {
    this.networkId = networkId;
    this.peerValidators = peerValidators;
    this.scheduler = scheduler;
    this.blockchain = blockchain;
    this.mergePeerFilter = mergePeerFilter;
    this.shutdown = new CountDownLatch(1);
    this.genesisHash = blockchain.getBlockHashByNumber(0L).orElse(Hash.ZERO);

    this.forkIdManager = forkIdManager;

    this.silPeers = silPeers;
    this.silMessages = silMessages;
    this.silContext = silContext;

    this.blockBroadcaster =
        new BlockBroadcaster(silContext, ethereumWireProtocolConfiguration.getMaxMessageSize());

    this.supportedCapabilities = calculateCapabilities(ethereumWireProtocolConfiguration);

    subscribeBlockRangeBroadcaster(silContext, blockchain);

    // Run validators
    for (final PeerValidator peerValidator : this.peerValidators) {
      PeerValidatorRunner.runValidator(silContext, peerValidator);
    }

    // Set up request handlers
    new SilServer(blockchain, transactionPool, silMessages, ethereumWireProtocolConfiguration);
  }

  @VisibleForTesting
  public SilProtocolManager(
      final Blockchain blockchain,
      final BigInteger networkId,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final SilProtocolConfiguration ethereumWireProtocolConfiguration,
      final SilPeers silPeers,
      final SilMessages silMessages,
      final SilContext silContext,
      final List<PeerValidator> peerValidators,
      final Optional<MergePeerFilter> mergePeerFilter,
      final SynchronizerConfiguration synchronizerConfiguration,
      final SilScheduler scheduler) {
    this(
        blockchain,
        networkId,
        worldStateArchive,
        transactionPool,
        ethereumWireProtocolConfiguration,
        silPeers,
        silMessages,
        silContext,
        peerValidators,
        mergePeerFilter,
        synchronizerConfiguration,
        scheduler,
        new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList()));
  }

  public SilContext silContext() {
    return silContext;
  }

  public BlockBroadcaster getBlockBroadcaster() {
    return blockBroadcaster;
  }

  @Override
  public String getSupportedProtocol() {
    return SilProtocol.NAME;
  }

  private List<Capability> calculateCapabilities(
      final SilProtocolConfiguration silProtocolConfiguration) {
    final List<Capability> capabilities = new ArrayList<>();

    capabilities.add(SilProtocol.SIL68);
    capabilities.add(SilProtocol.SIL69);
    capabilities.add(SilProtocol.SIL70);
    capabilities.add(SilProtocol.SIL71);
    capabilities.removeIf(cap -> cap.getVersion() > silProtocolConfiguration.getMaxEthCapability());
    capabilities.removeIf(cap -> cap.getVersion() < silProtocolConfiguration.getMinEthCapability());

    if (capabilities.isEmpty()) {
      throw new IllegalStateException(
          "No supported Sil protocol capabilities found. "
              + "Check the configuration for min and max Sil protocol versions.");
    }
    return Collections.unmodifiableList(capabilities);
  }

  private BlockRangeBroadcaster subscribeBlockRangeBroadcaster(
      final SilContext silContext, final Blockchain blockchain) {
    final boolean hasSupportForBlockRangeMessage =
        supportedCapabilities.stream()
            .anyMatch(
                cap ->
                    SilProtocol.get()
                        .isValidMessageCode(
                            cap.getVersion(), SilProtocolMessages.BLOCK_RANGE_UPDATE));
    return hasSupportForBlockRangeMessage
        ? new BlockRangeBroadcaster(silContext, blockchain)
        : null;
  }

  @Override
  public int getHighestProtocolVersion() {
    return getSupportedCapabilities().stream()
        .max(Comparator.comparing(Capability::getVersion))
        .map(Capability::getVersion)
        .orElse(0);
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  @Override
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      LOG.atInfo().setMessage("Stopping {} Subprotocol.").addArgument(getSupportedProtocol()).log();
      scheduler.stop();
      shutdown.countDown();
    } else {
      LOG.atInfo()
          .setMessage("Attempted to stop already stopped {} Subprotocol.")
          .addArgument(this::getSupportedProtocol)
          .log();
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    shutdown.await();
    scheduler.awaitStop();
    LOG.atInfo()
        .setMessage("{} Subprotocol stopped.")
        .addArgument(this::getSupportedProtocol)
        .log();
  }

  @Override
  public void processMessage(final Capability capability, final Message message) {
    checkArgument(
        getSupportedCapabilities().contains(capability),
        "Unsupported capability passed to processMessage(): " + capability);
    final MessageData messageData = message.getData();
    final int code = messageData.getCode();
    SilProtocolLogger.logProcessMessage(capability, code);
    final SilPeer silPeer = silPeers.peer(message.getConnection());
    if (silPeer == null) {
      LOG.atDebug()
          .setMessage("Ignoring message received from unknown peer connection: {}")
          .addArgument(message::getConnection)
          .log();
      return;
    }

    // Handle STATUS processing
    if (code == SilProtocolMessages.STATUS) {
      handleStatusMessage(silPeer, message);
      return;
    } else if (!silPeer.statusHasBeenReceived()) {
      // Peers are required to send status messages before any other message type
      LOG.atDebug()
          .setMessage(
              "{} requires a Status ({}) message to be sent first.  Instead, received message {} (BREACH_OF_PROTOCOL).  Disconnecting from {}.")
          .addArgument(() -> this.getClass().getSimpleName())
          .addArgument(SilProtocolMessages.STATUS)
          .addArgument(code)
          .addArgument(silPeer::toString)
          .log();
      silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_RECEIVED_OTHER_MESSAGE_BEFORE_STATUS);
      return;
    }

    if (this.mergePeerFilter.isPresent()) {
      if (this.mergePeerFilter.get().disconnectIfGossipingBlocks(message, silPeer)) {
        LOG.atDebug()
            .setMessage("Post-merge disconnect: peer still gossiping blocks {}")
            .addArgument(silPeer::toString)
            .log();
        handleDisconnect(
            silPeer.getConnection(), DisconnectReason.SUBPROTOCOL_TRIGGERED_POW_BLOCKS, false);
        return;
      }
    }

    final SilMessage silMessage = new SilMessage(silPeer, messageData);

    if (!silPeer.validateReceivedMessage(silMessage, getSupportedProtocol())) {
      LOG.debug(
          "Unsolicited message received {} (BREACH_OF_PROTOCOL), disconnecting from SilPeer: {}",
          silMessage.getData().getCode(),
          silPeer);
      silPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL_UNSOLICITED_MESSAGE_RECEIVED);
      return;
    }

    Optional<MessageData> maybeResponseData = Optional.empty();
    try {
      // This will handle responses
      silPeers.dispatchMessage(silPeer, silMessage, getSupportedProtocol());

      // This will handle requests
      if (SilProtocol.requestIdCompatible(code)) {
        final Map.Entry<BigInteger, MessageData> requestIdAndEthMessage =
            silMessage.getData().unwrapMessageData();
        maybeResponseData =
            silMessages
                .dispatch(new SilMessage(silPeer, requestIdAndEthMessage.getValue()), capability)
                .map(responseData -> responseData.wrapMessageData(requestIdAndEthMessage.getKey()));
      } else {
        maybeResponseData = silMessages.dispatch(silMessage, capability);
      }
    } catch (final FramingException e) {
      LOG.atDebug()
          .setMessage("Disconnecting peer {} due to decompression failure for message code {}")
          .addArgument(silPeer::getLoggableId)
          .addArgument(code)
          .setCause(e)
          .log();

      silPeer.disconnect(
          DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    } catch (final RLPException e) {
      LOG.atDebug()
          .setMessage(
              "Received malformed message code={} data={} (BREACH_OF_PROTOCOL), disconnecting: {}")
          .addArgument(code)
          .addArgument(messageData::getData)
          .addArgument(silPeer::toString)
          .setCause(e)
          .log();

      silPeer.disconnect(
          DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);
    } catch (final ProtocolViolationException e) {
      LOG.atDebug()
          .setMessage("Received invalid message {} ({}), disconnecting: {}, {}")
          .addArgument(messageData::getData)
          .addArgument(e::getReason)
          .addArgument(silPeer::toString)
          .addArgument(e::toString)
          .log();

      silPeer.disconnect(e.getReason());
    }
    maybeResponseData.ifPresent(
        responseData -> {
          try {
            silPeer.send(responseData, getSupportedProtocol());
          } catch (final PeerNotConnected __) {
            // Peer disconnected before we could respond - nothing to do
          }
        });
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {
    silPeers.registerNewConnection(connection, peerValidators);
    final SilPeer peer = silPeers.peer(connection);
    final Capability cap = connection.capability(getSupportedProtocol());
    StatusMessage status =
        StatusMessage.builder()
            .protocolVersion(cap.getVersion())
            .networkId(networkId)
            .bestHash(blockchain.getChainHeadHash())
            .genesisHash(genesisHash)
            .forkId(forkIdManager.getForkIdForChainHead())
            .apply(
                builder -> {
                  if (SilProtocol.isEth69Compatible(cap)) {
                    builder.blockRange(createBlockRange());
                  } else {
                    builder.totalDifficulty(blockchain.getChainHead().getTotalDifficulty());
                  }
                })
            .build();
    try {
      LOG.atTrace()
          .setMessage("Sending status message to {} for connection {}.")
          .addArgument(peer::getId)
          .addArgument(connection::toString)
          .log();
      peer.send(status, getSupportedProtocol(), connection);
      peer.registerStatusSent(connection);
    } catch (final PeerNotConnected peerNotConnected) {
      // Nothing to do.
    }
    LOG.atTrace().setMessage("{}").addArgument(silPeers::toString).log();
  }

  @Override
  public void handleDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    final boolean wasActiveConnection = silPeers.registerDisconnect(connection);
    LOG.atDebug()
        .setMessage("Disconnect - active Connection? {} - {} - {} - {} {} - {} peers left")
        .addArgument(wasActiveConnection)
        .addArgument(initiatedByPeer ? "Inbound" : "Outbound")
        .addArgument(reason::toString)
        .addArgument(() -> connection.getPeer().getLoggableId())
        .addArgument(() -> connection.getPeerInfo().getClientId())
        .addArgument(silPeers::peerCount)
        .log();
    if (initiatedByPeer && reason.isBreachOfProtocol()) {
      BreachOfProtocolLogger.log(connection, reason);
    }
    LOG.atTrace().setMessage("{}").addArgument(silPeers::toString).log();
  }

  private void handleStatusMessage(final SilPeer peer, final Message message) {
    final StatusMessage status = StatusMessage.readFrom(message.getData());
    final ForkId forkId = status.forkId();
    peer.getConnection().getPeer().setForkId(forkId);
    try {
      if (!status.networkId().equals(networkId)) {
        LOG.atDebug()
            .setMessage("Mismatched network id: {}, peer {}")
            .addArgument(status::networkId)
            .addArgument(() -> getPeerOrPeerId(peer))
            .log();
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED_MISMATCHED_NETWORK);
      } else if (!forkIdManager.peerCheck(forkId)) {
        LOG.atDebug()
            .setMessage("{} has matching network id ({}), but non-matching fork id: {}")
            .addArgument(() -> getPeerOrPeerId(peer))
            .addArgument(networkId::toString)
            .addArgument(forkId)
            .log();
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED_MISMATCHED_FORKID);
      } else if (forkIdManager.peerCheck(status.genesisHash())) {
        LOG.atDebug()
            .setMessage("{} has matching network id ({}), but non-matching genesis hash: {}")
            .addArgument(() -> getPeerOrPeerId(peer))
            .addArgument(networkId::toString)
            .addArgument(status::genesisHash)
            .log();
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED_MISMATCHED_GENESIS_HASH);
      } else if (mergePeerFilter.isPresent()
          && mergePeerFilter.get().disconnectIfPoW(status, peer)) {
        LOG.atDebug()
            .setMessage("Post-merge disconnect: peer still PoW {}")
            .addArgument(() -> getPeerOrPeerId(peer))
            .log();
        handleDisconnect(
            peer.getConnection(), DisconnectReason.SUBPROTOCOL_TRIGGERED_POW_DIFFICULTY, false);
      } else if (SilProtocol.isEth69Compatible(peer.getConnection().capability(SilProtocol.NAME))
          && !status.isEth69Compatible()) {
        LOG.atDebug()
            .setMessage("{} sent invalid status message {}")
            .addArgument(peer::toString)
            .addArgument(status::toString)
            .log();
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED_INVALID_STATUS_MESSAGE);
      } else {
        LOG.atDebug()
            .setMessage("Received status message from {}: {} with connection {}")
            .addArgument(peer::toString)
            .addArgument(status::toString)
            .addArgument(message::getConnection)
            .log();
        peer.registerStatusReceived(status, peer.getConnection());
      }
    } catch (final RLPException e) {
      LOG.atDebug()
          .setMessage("Unable to parse status message from peer {} {}")
          .addArgument(peer::getLoggableId)
          .addArgument(e)
          .log();
      // Parsing errors can happen when clients broadcast network ids outside the int range,
      // So just disconnect with "subprotocol" error rather than "breach of protocol".
      peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED_UNPARSABLE_STATUS);
    }
  }

  private Object getPeerOrPeerId(final SilPeer peer) {
    return LOG.isTraceEnabled() ? peer : peer.getLoggableId();
  }

  @Override
  public void blockMined(final Block block) {
    // This assumes the block has already been included in the chain
    final Difficulty totalDifficulty =
        blockchain
            .getTotalDifficultyByHash(block.getHash())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get total difficulty from blockchain for mined block."));
    blockBroadcaster.propagate(block, totalDifficulty);
  }

  private StatusMessage.BlockRange createBlockRange() {
    return blockchain
        .getEarliestBlockNumber()
        .map(
            earliestBlockNumber ->
                new StatusMessage.BlockRange(
                    earliestBlockNumber, blockchain.getChainHeadBlockNumber()))
        .orElseGet(() -> new StatusMessage.BlockRange(0, 0));
  }
}
