package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public interface DataColumnReqResp {

  SafeFuture<DataColumnSidecar> requestDataColumnSidecar(UInt256 nodeId, DataColumnIdentifier columnIdentifier);

  void flush();

  int getCurrentRequestLimit(UInt256 nodeId);
}
