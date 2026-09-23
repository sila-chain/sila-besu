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
package org.hyperledger.besu.sila.sil.manager;

import static org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason.INVALID_FIRST_BLOCK_RECEIPT_INDEX;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.sila.chain.Blockchain;
import org.hyperledger.besu.sila.core.BlockBody;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.Transaction;
import org.hyperledger.besu.sila.core.TransactionReceipt;
import org.hyperledger.besu.sila.core.encoding.EncodingContext;
import org.hyperledger.besu.sila.core.encoding.TransactionEncoder;
import org.hyperledger.besu.sila.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.sila.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.sila.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.sila.rlp.RLP;
import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.SilProtocolConfiguration;
import org.hyperledger.besu.sila.sil.manager.exceptions.ProtocolViolationException;
import org.hyperledger.besu.sila.sil.messages.BlockAccessListsMessage;
import org.hyperledger.besu.sila.sil.messages.BlockBodiesMessage;
import org.hyperledger.besu.sila.sil.messages.BlockHeadersMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockAccessListsMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.sila.sil.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.sila.sil.messages.GetPaginatedReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.GetPooledTransactionsMessage;
import org.hyperledger.besu.sila.sil.messages.GetReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.PaginatedReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.PooledTransactionsMessage;
import org.hyperledger.besu.sila.sil.messages.ReceiptsMessage;
import org.hyperledger.besu.sila.sil.messages.SilProtocolMessages;
import org.hyperledger.besu.sila.sil.transactions.TransactionPool;
import org.hyperledger.besu.sila.silaMainnet.block.access.list.BlockAccessList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SilServer {
  private static final Logger LOG = LoggerFactory.getLogger(SilServer.class);
  private final Blockchain blockchain;
  private final TransactionPool transactionPool;
  private final SilMessages silMessages;
  private final SilProtocolConfiguration silaWireProtocolConfiguration;

  SilServer(
      final Blockchain blockchain,
      final TransactionPool transactionPool,
      final SilMessages silMessages,
      final SilProtocolConfiguration silaWireProtocolConfiguration) {
    this.blockchain = blockchain;
    this.transactionPool = transactionPool;
    this.silMessages = silMessages;
    this.silaWireProtocolConfiguration = silaWireProtocolConfiguration;
    this.registerResponseConstructors();
  }

  private void registerResponseConstructors() {
    final int maxMessageSize = silaWireProtocolConfiguration.getMaxMessageSize();

    silMessages.registerResponseConstructor(
        SilProtocolMessages.GET_BLOCK_HEADERS,
        (peer, messageData, capability) ->
            constructGetHeadersResponse(
                blockchain,
                messageData,
                silaWireProtocolConfiguration.getMaxGetBlockHeaders(),
                maxMessageSize));
    silMessages.registerResponseConstructor(
        SilProtocolMessages.GET_BLOCK_BODIES,
        (peer, messageData, capability) ->
            constructGetBodiesResponse(
                blockchain,
                messageData,
                silaWireProtocolConfiguration.getMaxGetBlockBodies(),
                maxMessageSize));
    silMessages.registerResponseConstructor(
        SilProtocolMessages.GET_RECEIPTS,
        (peer, messageData, capability) -> {
          if (SilProtocol.isSil70Compatible(capability)) {
            return constructGetPaginatedReceiptsResponse(
                peer,
                blockchain,
                messageData,
                silaWireProtocolConfiguration.getMaxGetReceipts(),
                maxMessageSize);
          }
          return constructGetReceiptsResponse(
              blockchain,
              messageData,
              silaWireProtocolConfiguration.getMaxGetReceipts(),
              maxMessageSize,
              capability);
        });
    silMessages.registerResponseConstructor(
        SilProtocolMessages.GET_POOLED_TRANSACTIONS,
        (peer, messageData, capability) ->
            constructGetPooledTransactionsResponse(
                transactionPool,
                peer,
                messageData,
                silaWireProtocolConfiguration.getMaxGetPooledTransactions(),
                maxMessageSize));
    silMessages.registerResponseConstructor(
        SilProtocolMessages.GET_BLOCK_ACCESS_LISTS,
        (peer, messageData, capability) ->
            constructGetBlockAccessListsResponse(
                blockchain,
                messageData,
                silaWireProtocolConfiguration.getMaxGetBlockAccessLists(),
                maxMessageSize));
  }

  static MessageData constructGetHeadersResponse(
      final Blockchain blockchain,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize) {
    // Extract parameters from request
    final GetBlockHeadersMessage getHeaders = GetBlockHeadersMessage.readFrom(message);
    final Optional<Hash> hash = getHeaders.hash();
    final int skip = getHeaders.skip();
    final int maxHeaders = Math.min(requestLimit, getHeaders.maxHeaders());
    final boolean reversed = getHeaders.reverse();
    final BlockHeader firstHeader;
    // Query first header by hash or number depending on request arguments
    if (hash.isPresent()) {
      final Hash startHash = hash.get();
      firstHeader = blockchain.getBlockHeader(startHash).orElse(null);
    } else {
      final long firstNumber = getHeaders.blockNumber().getAsLong();
      firstHeader = blockchain.getBlockHeader(firstNumber).orElse(null);
    }

    // The initial header was not found, nothing to return
    if (firstHeader == null) {
      return BlockHeadersMessage.create(Collections.emptyList());
    }

    // Encode the first header
    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE;
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.startList();
    final Bytes firstEncodedHeader = RLP.encode(firstHeader::writeTo);
    if (responseSizeEstimate + firstEncodedHeader.size() > maxMessageSize) {
      return BlockHeadersMessage.create(Collections.emptyList());
    }
    responseSizeEstimate += firstEncodedHeader.size();
    rlp.writeRaw(firstEncodedHeader);
    // Collect and encode the remaining headers
    final long numberDelta = reversed ? -(skip + 1) : (skip + 1);
    for (int i = 1; i < maxHeaders; i++) {
      final long blockNumber = firstHeader.getNumber() + i * numberDelta;
      if (blockNumber < BlockHeader.GENESIS_BLOCK_NUMBER) {
        break;
      }
      final Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(blockNumber);
      if (maybeHeader.isEmpty()) {
        break;
      }
      final BytesValueRLPOutput headerRlp = new BytesValueRLPOutput();
      maybeHeader.get().writeTo(headerRlp);
      final int encodedSize = headerRlp.encodedSize();
      if (responseSizeEstimate + encodedSize > maxMessageSize) {
        break;
      }
      responseSizeEstimate += encodedSize;
      rlp.writeRaw(headerRlp.encoded());
    }
    rlp.endList();

    return BlockHeadersMessage.createUnsafe(rlp.encoded());
  }

  static MessageData constructGetBodiesResponse(
      final Blockchain blockchain,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize) {
    final GetBlockBodiesMessage getBlockBodiesMessage = GetBlockBodiesMessage.readFrom(message);
    final Iterable<Hash> hashes = getBlockBodiesMessage.hashes();

    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE;
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.startList();
    int count = 0;
    for (final Hash hash : hashes) {
      if (count >= requestLimit) {
        break;
      }
      count++;
      final Optional<BlockBody> maybeBody = blockchain.getBlockBody(hash);
      if (maybeBody.isEmpty()) {
        continue;
      }

      final BlockBody body = maybeBody.get();
      final BytesValueRLPOutput bodyOutput = new BytesValueRLPOutput();
      body.writeWrappedBodyTo(bodyOutput);
      final int encodedSize = bodyOutput.encodedSize();
      if (responseSizeEstimate + encodedSize > maxMessageSize) {
        break;
      }
      responseSizeEstimate += encodedSize;
      rlp.writeRaw(bodyOutput.encoded());
    }
    rlp.endList();
    return BlockBodiesMessage.createUnsafe(rlp.encoded());
  }

  static MessageData constructGetReceiptsResponse(
      final Blockchain blockchain,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize,
      final Capability cap) {
    final GetReceiptsMessage getReceipts = GetReceiptsMessage.readFrom(message);
    final Iterable<Hash> blockHashes = getReceipts.blockHashes();

    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE;
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.startList();
    int count = 0;
    for (final Hash blockHash : blockHashes) {
      if (count >= requestLimit) {
        break;
      }
      count++;
      final Optional<List<TransactionReceipt>> maybeReceipts = blockchain.getTxReceipts(blockHash);
      if (maybeReceipts.isEmpty()) {
        continue;
      }
      final BytesValueRLPOutput encodedReceipts = new BytesValueRLPOutput();
      encodedReceipts.startList();
      TransactionReceiptEncodingConfiguration encodingConfiguration =
          SilProtocol.isSil69Compatible(cap)
              ? TransactionReceiptEncodingConfiguration.SIL69_RECEIPT_CONFIGURATION
              : TransactionReceiptEncodingConfiguration.DEFAULT_NETWORK_CONFIGURATION;
      maybeReceipts
          .get()
          .forEach(
              r -> TransactionReceiptEncoder.writeTo(r, encodedReceipts, encodingConfiguration));
      encodedReceipts.endList();
      final int encodedSize = encodedReceipts.encodedSize();
      if (responseSizeEstimate + encodedSize > maxMessageSize) {
        break;
      }

      responseSizeEstimate += encodedSize;
      rlp.writeRaw(encodedReceipts.encoded());
    }
    rlp.endList();

    return ReceiptsMessage.createUnsafe(rlp.encoded());
  }

  static MessageData constructGetPaginatedReceiptsResponse(
      final SilPeer peer,
      final Blockchain blockchain,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize) {
    final GetPaginatedReceiptsMessage getPaginatedReceipts =
        GetPaginatedReceiptsMessage.readFrom(message);
    final Iterable<Hash> blockHashes = getPaginatedReceipts.blockHashes();

    final var blockReceiptsRLPs = new ArrayList<BytesValueRLPOutput>(requestLimit);

    int skipBefore = getPaginatedReceipts.firstBlockReceiptIndex();
    // Account for the outer list header and the lastBlockIncomplete scalar (max 2 bytes).
    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE + 2;
    boolean lastBlockIncomplete = false;

    int count = 0;
    for (final Hash blockHash : blockHashes) {
      if (count >= requestLimit) {
        break;
      }
      count++;
      final Optional<List<TransactionReceipt>> maybeReceipts = blockchain.getTxReceipts(blockHash);
      if (maybeReceipts.isEmpty()) {
        LOG.debug(
            "Invalid request from peer {}, block {} does not exists, returning", peer, blockHash);
        break;
      }

      final List<TransactionReceipt> blockReceipts = maybeReceipts.get();
      final List<TransactionReceipt> requestedReceipts;

      if (skipBefore > blockReceipts.size()) {
        throw new ProtocolViolationException(
            "Invalid request from peer %s, firstBlockReceiptIndex %d is greater than or equal the receipt count of %d for block %s"
                .formatted(peer, skipBefore, blockReceipts.size(), blockHash),
            INVALID_FIRST_BLOCK_RECEIPT_INDEX);
      }

      if (skipBefore > 0) {
        requestedReceipts = blockReceipts.subList(skipBefore, blockReceipts.size());
        skipBefore = 0;
      } else {
        requestedReceipts = blockReceipts;
      }

      // Account for this block's own list header before processing its receipts.
      responseSizeEstimate += RLP.MAX_PREFIX_SIZE;

      final BytesValueRLPOutput encodedBlockReceipts = new BytesValueRLPOutput();
      encodedBlockReceipts.startList();

      for (final TransactionReceipt receipt : requestedReceipts) {
        final BytesValueRLPOutput encodedReceipt = new BytesValueRLPOutput();
        TransactionReceiptEncoder.writeTo(
            receipt,
            encodedReceipt,
            TransactionReceiptEncodingConfiguration.SIL69_RECEIPT_CONFIGURATION);
        if (responseSizeEstimate + encodedReceipt.encodedSize() + RLP.MAX_PREFIX_SIZE
            > maxMessageSize) {
          lastBlockIncomplete = true;
          break;
        }
        responseSizeEstimate += encodedReceipt.encodedSize();
        encodedBlockReceipts.writeRaw(encodedReceipt.encoded());
      }

      encodedBlockReceipts.endList();
      blockReceiptsRLPs.add(encodedBlockReceipts);
      if (lastBlockIncomplete) {
        break;
      }
    }

    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.writeLongScalar(lastBlockIncomplete ? 1 : 0);
    rlp.startList();
    blockReceiptsRLPs.forEach(r -> rlp.writeRaw(r.encoded()));
    rlp.endList();

    final Bytes encodedResponse = rlp.encoded();
    LOG.trace(
        "Returning paginated receipts for {} blocks, with last block incomplete {}, enconded size {}",
        blockReceiptsRLPs.size(),
        lastBlockIncomplete,
        encodedResponse.size());
    return PaginatedReceiptsMessage.createUnsafe(encodedResponse, lastBlockIncomplete);
  }

  static MessageData constructGetBlockAccessListsResponse(
      final Blockchain blockchain,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize) {
    final GetBlockAccessListsMessage getBlockAccessLists =
        GetBlockAccessListsMessage.readFrom(message);
    final Iterable<Hash> blockHashes = getBlockAccessLists.blockHashes();

    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE;
    final List<Optional<BlockAccessList>> blockAccessLists = new ArrayList<>();
    int count = 0;
    for (final Hash blockHash : blockHashes) {
      if (count >= requestLimit) {
        break;
      }
      count++;

      final Optional<BlockAccessList> maybeBlockAccessList =
          blockchain.getBlockAccessList(blockHash);
      final BytesValueRLPOutput balOutput = new BytesValueRLPOutput();
      if (maybeBlockAccessList.isPresent()) {
        final BlockAccessList blockAccessList = maybeBlockAccessList.get();
        if (blockAccessList.rawRlp().isPresent()) {
          balOutput.writeBytes(blockAccessList.rawRlp().get());
        } else {
          throw new IllegalStateException("Expected BAL read from storage to contain RLP bytes");
        }
      } else {
        balOutput.writeBytes(Bytes.EMPTY);
      }

      final int encodedSize = balOutput.encodedSize();
      if (responseSizeEstimate + encodedSize > maxMessageSize) {
        break;
      }
      responseSizeEstimate += encodedSize;
      blockAccessLists.add(maybeBlockAccessList);
    }

    return BlockAccessListsMessage.create(blockAccessLists);
  }

  static MessageData constructGetPooledTransactionsResponse(
      final TransactionPool transactionPool,
      final SilPeer peer,
      final MessageData message,
      final int requestLimit,
      final int maxMessageSize) {
    final GetPooledTransactionsMessage getPooledTransactions =
        GetPooledTransactionsMessage.readFrom(message);
    final Iterable<Hash> hashes = getPooledTransactions.pooledTransactions();

    final boolean traceEnabled = LOG.isTraceEnabled();
    final Iterable<Hash> hashesToProcess;
    if (traceEnabled) {
      final List<Hash> requested = new ArrayList<>();
      hashes.forEach(requested::add);
      LOG.atTrace()
          .setMessage("Requested pooled transactions: peer={}, requested hashes={}")
          .addArgument(peer)
          .addArgument(requested)
          .log();
      hashesToProcess = requested;
    } else {
      hashesToProcess = hashes;
    }

    int responseSizeEstimate = RLP.MAX_PREFIX_SIZE;
    final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
    rlp.startList();
    final List<Hash> returnedHashes = traceEnabled ? new ArrayList<>() : null;
    int requestedCount = 0;
    int returnedCount = 0;
    for (final Hash hash : hashesToProcess) {
      if (requestedCount >= requestLimit) {
        break;
      }
      requestedCount++;
      final Optional<Transaction> maybeTx = transactionPool.getTransactionByHash(hash);
      if (maybeTx.isEmpty()) {
        continue;
      }

      final BytesValueRLPOutput txRlp = new BytesValueRLPOutput();
      TransactionEncoder.encodeRLP(maybeTx.get(), txRlp, EncodingContext.POOLED_TRANSACTION);
      final int encodedSize = txRlp.encodedSize();
      if (responseSizeEstimate + encodedSize > maxMessageSize) {
        break;
      }

      responseSizeEstimate += encodedSize;
      rlp.writeRaw(txRlp.encoded());
      returnedCount++;
      if (returnedHashes != null) {
        returnedHashes.add(hash);
      }
    }
    rlp.endList();

    if (traceEnabled) {
      LOG.atTrace()
          .setMessage("Sending pooled transactions: peer={}, returned hashes={}, notFoundCount={}")
          .addArgument(peer)
          .addArgument(returnedHashes)
          .addArgument(requestedCount - returnedCount)
          .log();
    }

    return PooledTransactionsMessage.createUnsafe(rlp.encoded());
  }
}
