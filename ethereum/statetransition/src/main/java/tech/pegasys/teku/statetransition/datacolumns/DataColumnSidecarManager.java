package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

import java.util.Optional;

public interface DataColumnSidecarManager {

  public static DataColumnSidecarManager NOOP =
      (sidecar, arrivalTimestamp) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT);

  public static DataColumnSidecarManager create(DataColumnSidecarGossipValidator validator) {
    // TODO
    return (sidecar, arrivalTimestamp) -> validator.validate(sidecar);
  }

  SafeFuture<InternalValidationResult> onDataColumnSidecarGossip(DataColumnSidecar sidecar, Optional<UInt64> arrivalTimestamp);
}
