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
package org.hyperledger.besu.sila.sil.peervalidation;

import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.manager.SilPeer;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerValidatorRunner {
  private static final Logger LOG = LoggerFactory.getLogger(PeerValidatorRunner.class);
  protected final SilContext silContext;
  private final PeerValidator peerValidator;

  PeerValidatorRunner(final SilContext silContext, final PeerValidator peerValidator) {
    this.silContext = silContext;
    this.peerValidator = peerValidator;

    silContext.getSilPeers().subscribeConnect(this::checkPeer);
  }

  public static void runValidator(final SilContext silContext, final PeerValidator peerValidator) {
    new PeerValidatorRunner(silContext, peerValidator);
  }

  public void checkPeer(final SilPeer silPeer) {
    if (peerValidator.canBeValidated(silPeer)) {
      peerValidator
          .validatePeer(silContext, silPeer)
          .whenComplete(
              (validated, err) -> {
                if (err != null || !validated) {
                  // Disconnect invalid peer
                  disconnectPeer(silPeer);
                } else {
                  silPeer.markValidated(peerValidator);
                }
              });
    } else if (!silPeer.isDisconnected()) {
      scheduleNextCheck(silPeer);
    }
  }

  protected void disconnectPeer(final SilPeer silPeer) {
    LOG.debug(
        "Disconnecting from peer {} marked invalid by {}",
        silPeer,
        peerValidator.getClass().getSimpleName());
    silPeer.disconnect(peerValidator.getDisconnectReason(silPeer));
  }

  protected void scheduleNextCheck(final SilPeer silPeer) {
    final Duration timeout = peerValidator.nextValidationCheckTimeout(silPeer);
    silContext.getScheduler().scheduleFutureTask(() -> checkPeer(silPeer), timeout);
  }
}
