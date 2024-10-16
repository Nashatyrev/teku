package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class DasByRootResponseLogger
    extends AbstractDasResponseLogger<DataColumnSidecarsByRootRequestMessage> {
  public DasByRootResponseLogger(
      TimeProvider timeProvider,
      Direction direction,
      ReqRespMethodLogger.PeerId peerId,
      DataColumnSidecarsByRootRequestMessage dataColumnIdentifiers, Logger logger, Level logLevel) {
    super(timeProvider, "data_column_sidecars_by_root", direction, peerId, dataColumnIdentifiers, logger, logLevel);
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
