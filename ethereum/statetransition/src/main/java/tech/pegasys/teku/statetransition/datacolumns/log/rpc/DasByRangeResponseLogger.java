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
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

class DasByRangeResponseLogger
    extends AbstractDasResponseLogger<DasReqRespLogger.ByRangeRequest> {
  public DasByRangeResponseLogger(
      TimeProvider timeProvider,
      Direction direction,
      LoggingPeerId peerId,
      DasReqRespLogger.ByRangeRequest request,
      Logger logger,
      Level logLevel) {
    super(
        timeProvider,
        "data_column_sidecars_by_range",
        direction,
        peerId,
        request,
        logger,
        logLevel);
  }

  @Override
  protected int requestedMaxCount() {
    return request.slotCount() * request.columnIndexes().size();
  }

  @Override
  protected String maxOrNot() {
    return " max";
  }

  @Override
  protected String requestToString() {
    return "[startSlot = "
        + request.startSlot()
        + ", count = "
        + request.slotCount()
        + ", columns = "
        + StringifyUtil.toIntRangeString(
            request.columnIndexes().stream().map(UInt64::intValue).toList())
        + "]";
  }
}
