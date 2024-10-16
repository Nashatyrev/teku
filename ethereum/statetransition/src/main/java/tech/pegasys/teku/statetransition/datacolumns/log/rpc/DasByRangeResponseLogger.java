package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

class DasByRangeResponseLogger
    extends AbstractDasResponseLogger<DataColumnSidecarsByRangeRequestMessage> {
  public DasByRangeResponseLogger(
      TimeProvider timeProvider,
      Direction direction,
      ReqRespMethodLogger.PeerId peerId,
      DataColumnSidecarsByRangeRequestMessage dataColumnIdentifiers, Logger logger, Level logLevel) {
    super(timeProvider, "data_column_sidecars_by_range", direction, peerId, dataColumnIdentifiers,logger,logLevel);
  }

  @Override
  protected String requestToString() {
    return "[startSlot = "
        + request.getStartSlot()
        + ", count = "
        + request.getCount()
        + ", columns = "
        + StringifyUtil.toIntRangeString(
            request.getColumns().stream().map(UInt64::intValue).toList()) + "]";
  }
}
