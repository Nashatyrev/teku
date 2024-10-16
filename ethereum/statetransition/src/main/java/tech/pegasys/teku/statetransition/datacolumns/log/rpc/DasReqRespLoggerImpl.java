/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;

class DasReqRespLoggerImpl implements DasReqRespLogger {

  private final TimeProvider timeProvider;
  private final Logger logger = LogManager.getLogger(DasReqRespLogger.class);
  private final Level logLevel = Level.DEBUG;

  ReqRespMethodLogger<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>
      byRootMethodLogger =
          new ReqRespMethodLogger<>() {
            @Override
            public ResponseLogger<DataColumnSidecar> onInboundRequest(
                PeerId fromPeer, DataColumnSidecarsByRootRequestMessage request) {
              return new DasByRootResponseLogger(
                  timeProvider,
                  AbstractResponseLogger.Direction.INBOUND,
                  fromPeer,
                  request,
                  logger,
                  logLevel);
            }

            @Override
            public ResponseLogger<DataColumnSidecar> onOutboundRequest(
                PeerId toPeer, DataColumnSidecarsByRootRequestMessage request) {
              return new DasByRootResponseLogger(
                  timeProvider,
                  AbstractResponseLogger.Direction.OUTBOUND,
                  toPeer,
                  request,
                  logger,
                  logLevel);
            }
          };

  ReqRespMethodLogger<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>
      byRangeMethodLogger =
          new ReqRespMethodLogger<>() {
            @Override
            public ResponseLogger<DataColumnSidecar> onInboundRequest(
                PeerId fromPeer, DataColumnSidecarsByRangeRequestMessage request) {
              return new DasByRangeResponseLogger(
                  timeProvider,
                  AbstractResponseLogger.Direction.INBOUND,
                  fromPeer,
                  request,
                  logger,
                  logLevel);
            }

            @Override
            public ResponseLogger<DataColumnSidecar> onOutboundRequest(
                PeerId toPeer, DataColumnSidecarsByRangeRequestMessage request) {
              return new DasByRangeResponseLogger(
                  timeProvider,
                  AbstractResponseLogger.Direction.OUTBOUND,
                  toPeer,
                  request,
                  logger,
                  logLevel);
            }
          };

  public DasReqRespLoggerImpl(TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  @Override
  public ReqRespMethodLogger<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>
      getDataColumnSidecarsByRootLogger() {
    return isLoggingEnabled() ? byRootMethodLogger : ReqRespMethodLogger.noop();
  }

  @Override
  public ReqRespMethodLogger<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>
      getDataColumnSidecarsByRangeLogger() {
    return isLoggingEnabled() ? byRangeMethodLogger : ReqRespMethodLogger.noop();
  }

  private boolean isLoggingEnabled() {
    return logger.getLevel().compareTo(logLevel) <= 0;
  }
}
