package tech.pegasys.teku.statetransition.datacolumns.log;

import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface DasGossipLogger {

  void onGossipInboundReceive(DataColumnSidecar sidecar);

  void onGossipInboundValidate(DataColumnSidecar sidecar, InternalValidationResult validationResult);

  void onGossipOutboundPublish(DataColumnSidecar sidecar);

  void onGossipOutboundPublishError(DataColumnSidecar sidecar, Throwable err);
}
