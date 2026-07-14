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
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.SnapProtocol;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.sila.sil.messages.GetPooledTransactionsMessage;
import org.hyperledger.besu.sila.sil.messages.StatusMessage;
import org.hyperledger.besu.sila.sil.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.sila.sil.messages.snap.GetBlockAccessListsMessage;
import org.hyperledger.besu.sila.sil.messages.snap.GetByteCodesMessage;
import org.hyperledger.besu.sila.sil.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.sila.sil.messages.snap.GetTrieNodesMessage;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV1;
import org.hyperledger.besu.sila.sil.messages.snap.SnapV2;
import org.hyperledger.besu.sila.sil.peervalidation.PeerValidator;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilPeer implements Comparable<SilPeer> {
  private static final Logger LOG = LoggerFactory.getLogger(SilPeer.class);

  private static final int MAX_OUTSTANDING_REQUESTS = 5;

  private PeerConnection connection;

  private final int maxTrackedSeenBlocks = 300;

  private final Set<Hash> knownBlocks =
      Collections.newSetFromMap(
          Collections.synchronizedMap(
              new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<Hash, Boolean> eldest) {
                  return size() > maxTrackedSeenBlocks;
                }
              }));
  private final Bytes localNodeId;

  private final int maxMessageSize;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final ChainState chainHeadState = new ChainState();
  private final AtomicBoolean readyForRequests = new AtomicBoolean(false);
  private final AtomicBoolean statusHasBeenReceivedFromPeer = new AtomicBoolean(false);
  private final AtomicBoolean fullyValidated = new AtomicBoolean(false);
  private final AtomicInteger lastProtocolVersion = new AtomicInteger(0);

  private volatile long lastRequestTimestamp = 0;

  private final Map<String, Map<Integer, RequestManager>> requestManagers;

  private final AtomicReference<Consumer<SilPeer>> onStatusesExchanged = new AtomicReference<>();
  private final PeerReputation reputation = new PeerReputation();
  private final Map<PeerValidator, Boolean> validationStatus = new ConcurrentHashMap<>();
  private final Bytes id;
  private boolean isServingSnap = false;

  private static final Map<Integer, Integer> roundMessages;

  static {
    roundMessages = new HashMap<>();
    roundMessages.put(SilProtocolMessages.BLOCK_HEADERS, SilProtocolMessages.GET_BLOCK_HEADERS);
    roundMessages.put(SilProtocolMessages.BLOCK_BODIES, SilProtocolMessages.GET_BLOCK_BODIES);
    roundMessages.put(SilProtocolMessages.RECEIPTS, SilProtocolMessages.GET_RECEIPTS);
    roundMessages.put(
        SilProtocolMessages.POOLED_TRANSACTIONS, SilProtocolMessages.GET_POOLED_TRANSACTIONS);
    roundMessages.put(
        SilProtocolMessages.BLOCK_ACCESS_LISTS, SilProtocolMessages.GET_BLOCK_ACCESS_LISTS);

    roundMessages.put(SnapV1.ACCOUNT_RANGE, SnapV1.GET_ACCOUNT_RANGE);
    roundMessages.put(SnapV1.STORAGE_RANGE, SnapV1.GET_STORAGE_RANGE);
    roundMessages.put(SnapV1.BYTECODES, SnapV1.GET_BYTECODES);
    roundMessages.put(SnapV1.TRIE_NODES, SnapV1.GET_TRIE_NODES);
    roundMessages.put(SnapV2.BLOCK_ACCESS_LISTS, SnapV2.GET_BLOCK_ACCESS_LISTS);
  }

  @VisibleForTesting
  public SilPeer(
      final PeerConnection connection,
      final Consumer<SilPeer> onStatusesExchanged,
      final List<PeerValidator> peerValidators,
      final int maxMessageSize,
      final Clock clock,
      final List<NodeMessagePermissioningProvider> permissioningProviders,
      final Bytes localNodeId) {
    this.connection = connection;
    this.maxMessageSize = maxMessageSize;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.onStatusesExchanged.set(onStatusesExchanged);
    peerValidators.forEach(peerValidator -> validationStatus.put(peerValidator, false));
    fullyValidated.set(peerValidators.isEmpty());

    this.requestManagers = new ConcurrentHashMap<>();
    this.localNodeId = localNodeId;
    this.id = connection.getPeer().getId();
    initSilRequestManagers();
    initSnapRequestManagers();
  }

  private void initSilRequestManagers() {
    // sil protocol
    requestManagers.put(
        SilProtocol.NAME,
        Map.ofEntries(
            Map.entry(
                SilProtocolMessages.GET_BLOCK_HEADERS, new RequestManager(this, SilProtocol.NAME)),
            Map.entry(
                SilProtocolMessages.GET_BLOCK_BODIES, new RequestManager(this, SilProtocol.NAME)),
            Map.entry(SilProtocolMessages.GET_RECEIPTS, new RequestManager(this, SilProtocol.NAME)),
            Map.entry(
                SilProtocolMessages.GET_POOLED_TRANSACTIONS,
                new RequestManager(this, SilProtocol.NAME)),
            Map.entry(
                SilProtocolMessages.GET_BLOCK_ACCESS_LISTS,
                new RequestManager(this, SilProtocol.NAME))));
  }

  private void initSnapRequestManagers() {
    // snap protocol
    requestManagers.put(
        SnapProtocol.NAME,
        Map.ofEntries(
            Map.entry(SnapV1.GET_ACCOUNT_RANGE, new RequestManager(this, SnapProtocol.NAME)),
            Map.entry(SnapV1.GET_STORAGE_RANGE, new RequestManager(this, SnapProtocol.NAME)),
            Map.entry(SnapV1.GET_BYTECODES, new RequestManager(this, SnapProtocol.NAME)),
            Map.entry(SnapV1.GET_TRIE_NODES, new RequestManager(this, SnapProtocol.NAME)),
            Map.entry(SnapV2.GET_BLOCK_ACCESS_LISTS, new RequestManager(this, SnapProtocol.NAME))));
  }

  public void markValidated(final PeerValidator validator) {
    if (!validationStatus.containsKey(validator)) {
      throw new IllegalArgumentException("Attempt to update unknown validation status");
    }
    validationStatus.put(validator, true);
    fullyValidated.set(validationStatus.values().stream().allMatch(b -> b));
  }

  /**
   * Check if this peer has been fully validated.
   *
   * @return {@code true} if all peer validation logic has run and successfully validated this peer
   */
  public boolean isFullyValidated() {
    return fullyValidated.get();
  }

  public boolean isDisconnected() {
    return connection.isDisconnected();
  }

  public long addChainEstimatedHeightListener(final ChainState.EstimatedHeightListener listener) {
    return chainHeadState.addEstimatedHeightListener(listener);
  }

  public void removeChainEstimatedHeightListener(final long listenerId) {
    chainHeadState.removeEstimatedHeightListener(listenerId);
  }

  public void recordRequestTimeout(final String protocolName, final int requestCode) {
    LOG.atDebug()
        .setMessage("Timed out while waiting for response from peer {}")
        .addArgument(this::getLoggableId)
        .log();
    LOG.trace("Timed out while waiting for response from peer {}", this);
    reputation.recordRequestTimeout(protocolName, requestCode, this).ifPresent(this::disconnect);
  }

  public void recordUselessResponse(final String requestType) {
    LOG.atTrace()
        .setMessage("Received useless response for request type {} from peer {}")
        .addArgument(requestType)
        .addArgument(this::getLoggableId)
        .log();
    reputation.recordUselessResponse(System.currentTimeMillis(), this).ifPresent(this::disconnect);
  }

  public void recordUsefulResponse() {
    reputation.recordUsefulResponse();
  }

  public void disconnect(final DisconnectReason reason) {
    connection.disconnect(reason);
  }

  public RequestManager.ResponseStream send(final MessageData messageData) throws PeerNotConnected {
    return send(messageData, SilProtocol.NAME);
  }

  public RequestManager.ResponseStream send(
      final MessageData messageData, final String protocolName) throws PeerNotConnected {
    return send(messageData, protocolName, this.connection);
  }

  /**
   * This method is only used for sending the status message, as it is possible that we have
   * multiple connections to the same peer at that time.
   *
   * @param messageData the data to send
   * @param protocolName the protocol to use for sending
   * @param connectionToUse the connection to use for sending
   * @return the response stream from the peer
   * @throws PeerNotConnected if the peer is not connected
   */
  public RequestManager.ResponseStream send(
      final MessageData messageData,
      final String protocolName,
      final PeerConnection connectionToUse)
      throws PeerNotConnected {
    if (connectionToUse.getAgreedCapabilities().stream()
        .noneMatch(capability -> capability.getName().equalsIgnoreCase(protocolName))) {
      LOG.atDebug()
          .setMessage("Protocol {} unavailable for this peer {}")
          .addArgument(protocolName)
          .addArgument(this.getLoggableId())
          .log();
      return null;
    }
    if (permissioningProviders.stream()
        .anyMatch(
            p -> !p.isMessagePermitted(connectionToUse.getRemoteEnode(), messageData.getCode()))) {
      LOG.info(
          "Permissioning blocked sending of message code {} to {}",
          messageData.getCode(),
          this.getLoggableId());
      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "Permissioning blocked by providers {}",
            permissioningProviders.stream()
                .filter(
                    p ->
                        !p.isMessagePermitted(
                            connectionToUse.getRemoteEnode(), messageData.getCode())));
      }
      return null;
    }
    // Check message size is within limits
    if (messageData.getSize() > maxMessageSize) {
      // This is a bug or else a misconfiguration of the max message size.
      LOG.error(
          "Dropping {} message to peer ({}) which exceeds local message size limit of {} bytes.  Message code: {}, Message Size: {}",
          protocolName,
          this,
          maxMessageSize,
          messageData.getCode(),
          messageData.getSize());
      return null;
    }

    if (requestManagers.containsKey(protocolName)) {
      final Map<Integer, RequestManager> managers = this.requestManagers.get(protocolName);
      if (managers.containsKey(messageData.getCode())) {
        return sendRequest(managers.get(messageData.getCode()), messageData);
      }
    }

    connectionToUse.sendForProtocol(protocolName, messageData);
    return null;
  }

  public RequestManager.ResponseStream getHeadersByHash(
      final Hash hash, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(hash, maxHeaders, skip, reverse);
    final RequestManager requestManager =
        requestManagers.get(SilProtocol.NAME).get(SilProtocolMessages.GET_BLOCK_HEADERS);
    return sendRequest(requestManager, message);
  }

  public RequestManager.ResponseStream getHeadersByNumber(
      final long blockNumber, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(blockNumber, maxHeaders, skip, reverse);
    return sendRequest(
        requestManagers.get(SilProtocol.NAME).get(SilProtocolMessages.GET_BLOCK_HEADERS), message);
  }

  public RequestManager.ResponseStream getBodies(final List<Hash> blockHashes)
      throws PeerNotConnected {
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.create(blockHashes);
    return sendRequest(
        requestManagers.get(SilProtocol.NAME).get(SilProtocolMessages.GET_BLOCK_BODIES), message);
  }

  public RequestManager.ResponseStream getPooledTransactions(final List<Hash> hashes)
      throws PeerNotConnected {
    final GetPooledTransactionsMessage message = GetPooledTransactionsMessage.create(hashes);
    return sendRequest(
        requestManagers.get(SilProtocol.NAME).get(SilProtocolMessages.GET_POOLED_TRANSACTIONS),
        message);
  }

  public RequestManager.ResponseStream getSnapAccountRange(
      final Hash stateRoot, final Bytes32 startKeyHash, final Bytes32 endKeyHash)
      throws PeerNotConnected {
    final GetAccountRangeMessage getAccountRangeMessage =
        GetAccountRangeMessage.create(stateRoot, startKeyHash, endKeyHash);
    getAccountRangeMessage.setRootHash(Optional.of(stateRoot));
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV1.GET_ACCOUNT_RANGE),
        getAccountRangeMessage);
  }

  public RequestManager.ResponseStream getSnapStorageRange(
      final Hash stateRoot,
      final List<Bytes32> accountHashes,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash)
      throws PeerNotConnected {
    final GetStorageRangeMessage getStorageRangeMessage =
        GetStorageRangeMessage.create(stateRoot, accountHashes, startKeyHash, endKeyHash);
    getStorageRangeMessage.setRootHash(Optional.of(stateRoot));
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV1.GET_STORAGE_RANGE),
        getStorageRangeMessage);
  }

  public RequestManager.ResponseStream getSnapBytecode(
      final Hash stateRoot, final List<Bytes32> codeHashes) throws PeerNotConnected {
    final GetByteCodesMessage getByteCodes = GetByteCodesMessage.create(codeHashes);
    getByteCodes.setRootHash(Optional.of(stateRoot));
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV1.GET_BYTECODES), getByteCodes);
  }

  public RequestManager.ResponseStream getSnapTrieNode(
      final Hash stateRoot, final List<List<Bytes>> paths) throws PeerNotConnected {
    final GetTrieNodesMessage getTrieNodes = GetTrieNodesMessage.create(stateRoot, paths);
    getTrieNodes.setRootHash(Optional.of(stateRoot));
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV1.GET_TRIE_NODES), getTrieNodes);
  }

  public RequestManager.ResponseStream getSnapBlockAccessLists(final List<Hash> blockHashes)
      throws PeerNotConnected {
    final GetBlockAccessListsMessage getBlockAccessListsMessage =
        GetBlockAccessListsMessage.create(blockHashes);
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV2.GET_BLOCK_ACCESS_LISTS),
        getBlockAccessListsMessage);
  }

  public void setIsServingSnap(final boolean isServingSnap) {
    this.isServingSnap = isServingSnap;
  }

  public boolean isServingSnap() {
    return isServingSnap;
  }

  private RequestManager.ResponseStream sendRequest(
      final RequestManager requestManager, final MessageData messageData) throws PeerNotConnected {
    lastRequestTimestamp = clock.millis();
    return requestManager.dispatchRequest(
        msgData -> connection.sendForProtocol(requestManager.getProtocolName(), msgData),
        messageData);
  }

  /**
   * Determines the validity of a message received from a peer. A message is considered valid if
   * either of the following conditions are met: 1) The message is a request type message (e.g.
   * GET_BLOCK_HEADERS), or 2) The message is a response type message (e.g. BLOCK_HEADERS), the node
   * has made at least 1 request for that type of message (i.e. it has sent at least 1
   * GET_BLOCK_HEADERS request), and it has at least 1 outstanding request of that type which it
   * expects to receive a response for.
   *
   * @param message The message being validated
   * @param protocolName The protocol type of the message
   * @return true if the message is valid as per the above logic, otherwise false.
   */
  public boolean validateReceivedMessage(final SilMessage message, final String protocolName) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    return getRequestManager(protocolName, message.getData().getCode())
        .map(requestManager -> requestManager.outstandingRequests() != 0)
        .orElse(true);
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param silMessage the Sil message to dispatch
   * @param protocolName Specific protocol name if needed
   */
  Optional<RequestManager> dispatch(final SilMessage silMessage, final String protocolName) {
    checkArgument(
        silMessage.getPeer().equals(this), "Mismatched Sil message sent to peer for dispatch");
    final int messageCode = silMessage.getData().getCode();
    reputation.resetTimeoutCount(protocolName, messageCode);

    Optional<RequestManager> requestManager = getRequestManager(protocolName, messageCode);
    requestManager.ifPresentOrElse(
        localRequestManager -> localRequestManager.dispatchResponse(silMessage),
        () -> {
          LOG.trace(
              "Request message {} has just been received for protocol {}, peer {} ",
              messageCode,
              protocolName,
              this);
        });
    return requestManager;
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param silMessage the Sil message to dispatch
   */
  void dispatch(final SilMessage silMessage) {
    dispatch(silMessage, SilProtocol.NAME);
  }

  /**
   * Attempt to get a request manager for a received response-type message e.g. BLOCK_HEADERS. If
   * the message is a request-type message e.g. GET_BLOCK_HEADERS no request manager will exist so
   * Optional.empty() will be returned.
   *
   * @param protocolName the type of protocol the message is for
   * @param code the message code
   * @return a request manager for the received response message, or Optional.empty() if this is a
   *     request message
   */
  private Optional<RequestManager> getRequestManager(final String protocolName, final int code) {
    if (requestManagers.containsKey(protocolName)) {
      final Map<Integer, RequestManager> managers = requestManagers.get(protocolName);
      final Integer requestCode = roundMessages.getOrDefault(code, -1);
      if (managers.containsKey(requestCode)) {
        return Optional.of(managers.get(requestCode));
      }
    }
    return Optional.empty();
  }

  public Map<ImmutablePair<String, Integer>, AtomicInteger> timeoutCounts() {
    return reputation.timeoutCounts();
  }

  public PeerReputation getReputation() {
    return reputation;
  }

  void handleDisconnect() {
    LOG.trace("handleDisconnect - SilPeer {}", this);

    requestManagers.forEach(
        (protocolName, map) -> map.forEach((code, requestManager) -> requestManager.close()));
  }

  public void registerKnownBlock(final Hash hash) {
    knownBlocks.add(hash);
  }

  public void registerStatusSent(final PeerConnection connection) {
    synchronized (this) {
      connection.setStatusSent();
      maybeExecuteStatusesExchangedCallback(connection);
    }
  }

  public void registerStatusReceived(
      final StatusMessage statusMessage, final PeerConnection connection) {
    chainHeadState.statusReceived(statusMessage);
    lastProtocolVersion.set(statusMessage.protocolVersion());
    statusHasBeenReceivedFromPeer.set(true);
    synchronized (this) {
      connection.setStatusReceived();
      maybeExecuteStatusesExchangedCallback(connection);
    }
  }

  private void maybeExecuteStatusesExchangedCallback(final PeerConnection newConnection) {
    synchronized (this) {
      if (newConnection.getStatusExchanged()) {
        if (!this.connection.equals(newConnection)) {
          if (readyForRequests.get()) {
            // We have two connections that are ready for requests, figure out which connection to
            // keep
            if (compareDuplicateConnections(this.connection, newConnection) > 0) {
              LOG.trace("Changed connection from {} to {}", this.connection, newConnection);
              this.connection = newConnection;
            }
          } else {
            // use the new connection for now, as it is ready for requests, which the "old" one is
            // not
            this.connection = newConnection;
          }
        }
        readyForRequests.set(true);
        final Consumer<SilPeer> peerConsumer = onStatusesExchanged.getAndSet(null);
        if (peerConsumer != null) {
          LOG.trace("Status message exchange successful. {}", this);
          peerConsumer.accept(this);
        }
      }
    }
  }

  /**
   * Wait until status has been received and verified before using a peer.
   *
   * @return true if the peer is ready to accept requests for data.
   */
  public boolean readyForRequests() {
    return readyForRequests.get();
  }

  /**
   * True if the peer has sent its initial status message to us.
   *
   * @return true if the peer has sent its initial status message to us.
   */
  boolean statusHasBeenReceived() {
    return statusHasBeenReceivedFromPeer.get();
  }

  public boolean hasSeenBlock(final Hash hash) {
    return knownBlocks.contains(hash);
  }

  /**
   * Return This peer's current chain state.
   *
   * @return This peer's current chain state.
   */
  public ChainState chainState() {
    return chainHeadState;
  }

  public int getLastProtocolVersion() {
    return lastProtocolVersion.get();
  }

  /**
   * Return A read-only snapshot of this peer's current {@code chainState}
   *
   * @return A read-only snapshot of this peer's current {@code chainState}
   */
  public ChainHeadEstimate chainStateSnapshot() {
    return chainHeadState.getSnapshot();
  }

  public void registerHeight(final Hash blockHash, final long height) {
    chainHeadState.update(blockHash, height);
  }

  public void registerBlockRange(
      final Hash blockHash, final long height, final long earliestBlockHeight) {
    chainHeadState.update(blockHash, height, earliestBlockHeight);
  }

  public int outstandingRequests() {
    return requestManagers.values().stream()
        .flatMap(m -> m.values().stream())
        .mapToInt(RequestManager::outstandingRequests)
        .sum();
  }

  public long getLastRequestTimestamp() {
    return lastRequestTimestamp;
  }

  public boolean hasAvailableRequestCapacity() {
    return outstandingRequests() < MAX_OUTSTANDING_REQUESTS;
  }

  public Set<Capability> getAgreedCapabilities() {
    return connection.getAgreedCapabilities();
  }

  public PeerConnection getConnection() {
    return connection;
  }

  public Bytes nodeId() {
    return connection.getPeerInfo().getNodeId();
  }

  public boolean hasSupportForMessage(final int messageCode) {
    return getAgreedCapabilities().stream()
        .anyMatch(cap -> SilProtocol.get().isValidMessageCode(cap.getVersion(), messageCode));
  }

  @Override
  public String toString() {
    return String.format(
        "PeerId: %s %s, validated? %s, disconnected? %s, client: %s, %s, %s, isServingSnap %s, has height %s, connected for %s ms, capabilities: %s",
        getLoggableId(),
        reputation,
        isFullyValidated(),
        isDisconnected(),
        connection.getPeerInfo().getClientId(),
        connection,
        connection.getPeer().getEnodeURLString(),
        isServingSnap,
        chainHeadState.getEstimatedHeight(),
        System.currentTimeMillis() - connection.getInitiatedAt(),
        getAgreedCapabilities().stream()
            .map(Capability::toString)
            .collect(java.util.stream.Collectors.joining(", ", "[", "]")));
  }

  @NotNull
  public String getLoggableId() {
    // 8 bytes plus the 0x prefix is 18 characters
    return nodeId().toString().substring(0, 18) + "...";
  }

  @Override
  public int compareTo(final @NotNull SilPeer silPeer) {
    final int repCompare = this.reputation.compareTo(silPeer.reputation);
    if (repCompare != 0) return repCompare;

    final int headStateCompare =
        Long.compare(
            this.chainHeadState.getBestBlock().getNumber(),
            silPeer.chainHeadState.getBestBlock().getNumber());
    if (headStateCompare != 0) return headStateCompare;

    return getConnection().getPeerInfo().compareTo(silPeer.getConnection().getPeerInfo());
  }

  public Bytes getId() {
    return id;
  }

  /**
   * Compares two connections to the same peer to determine which connection should be kept
   *
   * @param a The first connection
   * @param b The second connection
   * @return A negative value if {@code a} should be kept, a positive value is {@code b} should be
   *     kept
   */
  private int compareDuplicateConnections(final PeerConnection a, final PeerConnection b) {

    if (a.isDisconnected() != b.isDisconnected()) {
      // One connection has failed - prioritize the one that hasn't failed
      return a.isDisconnected() ? 1 : -1;
    }

    final Bytes peerId = a.getPeer().getId();
    // peerId is the id of the other node
    if (a.inboundInitiated() != b.inboundInitiated()) {
      // If we have connections initiated in different directions, keep the connection initiated
      // by the node with the lower id
      if (localNodeId.compareTo(peerId) < 0) {
        return a.inboundInitiated() ? 1 : -1;
      } else {
        return a.inboundInitiated() ? -1 : 1;
      }
    }
    // Otherwise, keep older connection
    LOG.atTrace()
        .setMessage("comparing timestamps {} with {}")
        .addArgument(a.getInitiatedAt())
        .addArgument(b.getInitiatedAt())
        .log();
    return a.getInitiatedAt() < b.getInitiatedAt() ? -1 : 1;
  }

  @FunctionalInterface
  public interface DisconnectCallback {
    void onDisconnect(SilPeer peer);
  }
}
