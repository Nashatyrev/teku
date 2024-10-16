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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

abstract class AbstractDasResponseLogger<TRequest>
    extends AbstractResponseLogger<TRequest, DataColumnSidecar, DataColumnSlotAndIdentifier> {

  private final String methodName;

  public AbstractDasResponseLogger(
      TimeProvider timeProvider,
      String methodName,
      Direction direction,
      ReqRespMethodLogger.PeerId peerId,
      TRequest request,
      Logger logger,
      Level logLevel) {
    super(
        timeProvider,
        direction,
        peerId,
        request,
        DataColumnSlotAndIdentifier::fromDataColumn,
        logger,
        logLevel);
    this.methodName = methodName;
  }

  protected abstract String requestToString();

  @Override
  protected void responseComplete(
      List<Timestamped<DataColumnSlotAndIdentifier>> responseSummaries,
      Optional<Throwable> result) {

    long curTime = timeProvider.getTimeInMillis().longValue();
    String fromTo = direction == Direction.INBOUND ? "from" : "to";
    String responsesTime =
        responseSummaries.isEmpty()
            ? "0 ms"
            : msAgoString(timeProvider.getTimeInMillis().longValue(), responseSummaries);
    final String responseString;
    if (result.isEmpty()) {
      responseString = responsesToString(responseSummaries);
    } else if (responseSummaries.isEmpty()) {
      responseString = "error: " + result.get();
    } else {
      responseString =
          "columns then error: "
              + responsesToString(responseSummaries)
              + ", error: "
              + result.get();
    }
    getLogger()
        .log(
            getLogLevel(),
            "DAS ReqResp {} {} {} peer {}: request: {} ms ago {}, response: {} ago {}",
            direction,
            methodName,
            fromTo,
            peerId,
            curTime - requestTime,
            requestToString(),
            responsesTime,
            responseString);
  }

  protected String responsesToString(List<Timestamped<DataColumnSlotAndIdentifier>> responses) {
    if (responses.isEmpty()) {
      return "<empty>";
    }

    SortedMap<SlotAndBlockRoot, List<Timestamped<DataColumnSlotAndIdentifier>>> responsesByBlock =
        new TreeMap<>(
            responses.stream()
                .collect(Collectors.groupingBy(AbstractDasResponseLogger::blockIdFromResponse)));
    return responses.size()
        + " columns: "
        + responsesByBlock.entrySet().stream()
            .map(
                entry ->
                    blockIdString(entry.getKey())
                        + " colIdxs: "
                        + blockResponsesToString(entry.getValue()))
            .collect(Collectors.joining(", "));
  }

  protected String blockResponsesToString(
      List<Timestamped<DataColumnSlotAndIdentifier>> responses) {
    return StringifyUtil.toIntRangeString(
        responses.stream().map(it -> it.value().columnIndex().intValue()).toList());
  }

  private static String blockIdString(SlotAndBlockRoot blockId) {
    return "#"
        + blockId.getSlot()
        + " (0x"
        + LogFormatter.formatAbbreviatedHashRoot(blockId.getBlockRoot())
        + ")";
  }

  private static String msAgoString(long curTime, List<? extends Timestamped<?>> events) {
    long firstMillisAgo =
        curTime - events.stream().min(Comparator.comparing(Timestamped::time)).orElseThrow().time();
    long lastMillisAgo =
        curTime - events.stream().max(Comparator.comparing(Timestamped::time)).orElseThrow().time();
    return (lastMillisAgo == firstMillisAgo
        ? lastMillisAgo + "ms"
        : lastMillisAgo + "ms-" + firstMillisAgo + "ms");
  }

  private static SlotAndBlockRoot blockIdFromResponse(
      Timestamped<DataColumnSlotAndIdentifier> response) {
    return new SlotAndBlockRoot(response.value().slot(), response.value().blockRoot());
  }
}
