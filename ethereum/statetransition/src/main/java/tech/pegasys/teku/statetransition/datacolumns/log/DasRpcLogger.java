package tech.pegasys.teku.statetransition.datacolumns.log;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.List;

public interface DasRpcLogger {

  void onInboundRpcByRootRequest(String requestId, UInt256 nodeId, List<DataColumnIdentifier> columnIdentifiers);

  void onInboundRpcByRootResponse(String requestId, List<DataColumnSidecar> sidecars);

  void onOutboundRpcByRootRequest(String requestId, UInt256 nodeId, List<DataColumnIdentifier> columnIdentifiers);

  void onOutboundRpcByRootResponse(String requestId, List<DataColumnSidecar> sidecars);

  void onOutboundRpcByRootError(String requestId, List<DataColumnSidecar> sidecars);

}
