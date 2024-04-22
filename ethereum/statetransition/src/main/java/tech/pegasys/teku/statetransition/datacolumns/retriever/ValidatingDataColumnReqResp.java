package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarValidator;

public class ValidatingDataColumnReqResp implements DataColumnReqResp {

  private final DataColumnPeerManager peerManager;
  private final DataColumnReqResp reqResp;
  private final DataColumnSidecarValidator validator;

  public ValidatingDataColumnReqResp(
      DataColumnPeerManager peerManager,
      DataColumnReqResp reqResp,
      DataColumnSidecarValidator validator) {
    this.peerManager = peerManager;
    this.reqResp = reqResp;
    this.validator = validator;
  }

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(UInt256 nodeId, DataColumnIdentifier columnIdentifier) {
    return reqResp
        .requestDataColumnSidecar(nodeId, columnIdentifier)
        .thenCompose(
            sidecar ->
                validator
                    .validate(sidecar)
                    .thenApply(__ -> sidecar)
                    .catchAndRethrow(
                        err -> peerManager.banNode(nodeId)));
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    return reqResp.getCurrentRequestLimit(nodeId);
  }

  @Override
  public void flush() {
    reqResp.flush();
  }
}
