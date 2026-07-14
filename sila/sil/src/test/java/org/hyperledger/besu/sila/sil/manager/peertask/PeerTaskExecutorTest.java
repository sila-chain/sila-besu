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
package org.hyperledger.besu.sila.sil.manager.peertask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.sila.sil.SilProtocol;
import org.hyperledger.besu.sila.sil.manager.SilPeer;
import org.hyperledger.besu.sila.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.sila.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.sila.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.sila.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class PeerTaskExecutorTest {
  private @Mock PeerSelector peerSelector;
  private @Mock PeerTaskRequestSender requestSender;
  private @Mock PeerTask<Object> peerTask;
  private @Mock SubProtocol subprotocol;
  private @Mock MessageData requestMessageData;
  private @Mock MessageData responseMessageData;
  private @Mock SilPeer silPeer;
  private AutoCloseable mockCloser;

  private PeerTaskExecutor peerTaskExecutor;

  @BeforeEach
  public void beforeTest() {
    mockCloser = MockitoAnnotations.openMocks(this);
    peerTaskExecutor = new PeerTaskExecutor(peerSelector, requestSender, new NoOpMetricsSystem());
    when(silPeer.getAgreedCapabilities()).thenReturn(Set.of(SilProtocol.LATEST));
  }

  @AfterEach
  public void afterTest() throws Exception {
    mockCloser.close();
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndSuccessfulFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {

    Object responseObject = new Object();

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any()))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).recordUsefulResponse();

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertSame(responseObject, result.result().get());
    assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPeerShouldBeDisconnected()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {

    Object responseObject = new Object();

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any()))
        .thenReturn(PeerTaskValidationResponse.NON_SEQUENTIAL_HEADERS_RETURNED);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer)
        .disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_NON_SEQUENTIAL_HEADERS);

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertSame(responseObject, result.result().get());
    assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPeerSuppliedMalformedRlp()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any()))
        .thenThrow(new MalformedRlpFromPeerException(new Exception(), Bytes.EMPTY));

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).disconnect(DisconnectReason.BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED);

    assertNotNull(result);
    assertFalse(result.result().isPresent());
    assertEquals(PeerTaskExecutorResponseCode.PEER_DISCONNECTED, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPartialSuccessfulFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {

    Object responseObject = new Object();

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any())).thenReturn(PeerTaskValidationResponse.NO_RESULTS_RETURNED);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithRetriesAndSuccessfulFlowAfterFirstFailure()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {
    Object responseObject = new Object();
    int requestMessageDataCode = 123;
    String protocolName = "snap";

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(2);

    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn(protocolName);
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenThrow(new TimeoutException())
        .thenReturn(responseMessageData);
    when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any()))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).recordRequestTimeout(protocolName, requestMessageDataCode);
    verify(silPeer).recordUsefulResponse();

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertSame(responseObject, result.result().get());
    assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPeerNotConnected()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenThrow(new PeerConnection.PeerNotConnected(""));

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    assertNotNull(result);
    assertTrue(result.result().isEmpty());
    assertEquals(PeerTaskExecutorResponseCode.PEER_DISCONNECTED, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndTimeoutException()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {
    int requestMessageDataCode = 123;
    String protocolName = "snap";

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn(protocolName);
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenThrow(new TimeoutException());
    when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).recordRequestTimeout(protocolName, requestMessageDataCode);

    assertNotNull(result);
    assertTrue(result.result().isEmpty());
    assertEquals(PeerTaskExecutorResponseCode.TIMEOUT, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndInvalidResponseMessage()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenThrow(new InvalidPeerTaskResponseException());

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).recordUselessResponse(null);

    assertNotNull(result);
    assertTrue(result.result().isEmpty());
    assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithNoRetriesAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {
    Object responseObject = new Object();

    when(peerSelector.getPeer(any(Predicate.class))).thenReturn(Optional.of(silPeer));

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithOtherPeer()).thenReturn(0);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn("subprotocol");
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any()))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, silPeer);

    verify(silPeer).recordUsefulResponse();

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertSame(responseObject, result.result().get());
    assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithPeerSwitchingAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          MalformedRlpFromPeerException {
    Object responseObject = new Object();
    int requestMessageDataCode = 123;
    String protocolName = "snap";
    SilPeer peer2 = Mockito.mock(SilPeer.class);

    when(peerSelector.getPeer(any(Predicate.class)))
        .thenReturn(Optional.of(silPeer))
        .thenReturn(Optional.of(peer2));

    when(peerTask.getRequestMessage(any())).thenReturn(requestMessageData);
    when(peerTask.getRetriesWithOtherPeer()).thenReturn(2);
    when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    when(subprotocol.getName()).thenReturn(protocolName);
    when(requestSender.sendRequest(subprotocol, requestMessageData, silPeer))
        .thenThrow(new TimeoutException());
    when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    when(requestSender.sendRequest(subprotocol, requestMessageData, peer2))
        .thenReturn(responseMessageData);
    when(peerTask.processResponse(any(), any())).thenReturn(responseObject);
    when(peerTask.validateResult(any()))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.execute(peerTask);

    verify(silPeer).recordRequestTimeout(protocolName, requestMessageDataCode);
    verify(peer2).recordUsefulResponse();

    assertNotNull(result);
    assertTrue(result.result().isPresent());
    assertSame(responseObject, result.result().get());
    assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }
}
