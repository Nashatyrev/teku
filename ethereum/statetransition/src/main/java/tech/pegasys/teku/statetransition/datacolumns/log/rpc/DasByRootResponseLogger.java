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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

class DasByRootResponseLogger
    extends AbstractDasResponseLogger<List<DataColumnIdentifier>> {

  public DasByRootResponseLogger(
      TimeProvider timeProvider,
      Direction direction,
      LoggingPeerId peerId,
      List<DataColumnIdentifier> dataColumnIdentifiers,
      Logger logger,
      Level logLevel) {
    super(
        timeProvider,
        "data_column_sidecars_by_root",
        direction,
        peerId,
        dataColumnIdentifiers,
        logger,
        logLevel);
  }

  @Override
  protected int requestedMaxCount() {
    return request.size();
  }

  @Override
  protected String maxOrNot() {
    return "";
  }

  @Override
  protected String requestToString() {
    Map<Bytes32, List<DataColumnIdentifier>> columnIdsByBlock =
        request.stream().collect(Collectors.groupingBy(DataColumnIdentifier::getBlockRoot));
    String columns =
        columnIdsByBlock.entrySet().stream()
            .map(
                e ->
                    "(0x"
                        + LogFormatter.formatAbbreviatedHashRoot(e.getKey())
                        + ") colIdxs: "
                        + StringifyUtil.toIntRangeString(
                            e.getValue().stream().map(it -> it.getIndex().intValue()).toList()))
            .collect(Collectors.joining(", "));
    return request.size() + " columns: " + columns;
  }
}
