package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRootReqResp;

import java.util.List;

public class LoggingBatchDataColumnsByRootReqResp implements BatchDataColumnsByRootReqResp {
  private final BatchDataColumnsByRootReqResp delegate;
  private final DasReqRespLogger logger;

  public LoggingBatchDataColumnsByRootReqResp(BatchDataColumnsByRootReqResp delegate, DasReqRespLogger logger) {
    this.delegate = delegate;
    this.logger = logger;
  }

  @Override
  public AsyncStream<DataColumnSidecar> requestDataColumnSidecarsByRoot(UInt256 nodeId, List<DataColumnIdentifier> columnIdentifiers) {
    return delegate.requestDataColumnSidecarsByRoot(nodeId, columnIdentifiers);
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    return delegate.getCurrentRequestLimit(nodeId);
  }
}
