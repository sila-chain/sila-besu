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
package org.hyperledger.besu.sila.sil.manager.task;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.sila.core.BlockHeader;
import org.hyperledger.besu.sila.core.SyncBlock;
import org.hyperledger.besu.sila.core.SyncBlockBody;
import org.hyperledger.besu.sila.sil.manager.SilContext;
import org.hyperledger.besu.sila.sil.messages.BlockBodiesMessage;
import org.hyperledger.besu.sila.silaMainnet.ProtocolSchedule;

import java.util.List;

/** Requests bodies from a peer by header, matches up headers to bodies, and returns blocks. */
public class GetSyncBlocksFromPeerTask
    extends AbstractGetBodiesFromPeerTask<SyncBlock, SyncBlockBody> {

  private GetSyncBlocksFromPeerTask(
      final SilContext silContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem,
      final ProtocolSchedule protocolSchedule) {
    super(protocolSchedule, silContext, headers, metricsSystem);
    checkArgument(!headers.isEmpty());
  }

  public static GetSyncBlocksFromPeerTask forHeaders(
      final SilContext silContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem,
      final ProtocolSchedule protocolSchedule) {
    return new GetSyncBlocksFromPeerTask(silContext, headers, metricsSystem, protocolSchedule);
  }

  @Override
  SyncBlock getBlock(final BlockHeader header, final SyncBlockBody body) {
    return new SyncBlock(header, body);
  }

  @Override
  List<SyncBlockBody> getBodies(
      final BlockBodiesMessage message, final ProtocolSchedule protocolSchedule) {
    return message.syncBodies(protocolSchedule);
  }

  @Override
  boolean bodyMatchesHeader(final SyncBlockBody body, final BlockHeader header) {
    final BodyIdentifier headerId = new BodyIdentifier(header);
    final BodyIdentifier bodyId = new BodyIdentifier(body);
    return headerId.equals(bodyId);
  }
}
