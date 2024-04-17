package tech.pegasys.teku.statetransition.validation;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;

public interface DataColumnSidecarGossipValidator {

  public static DataColumnSidecarGossipValidator NOOP =
      dataColumnSidecar -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT);

  public static DataColumnSidecarGossipValidator create(DataColumnSidecarValidator validator) {
    return dataColumnSidecar -> validator.validate(dataColumnSidecar)
        .handle((__, err) ->
            err == null ? InternalValidationResult.ACCEPT : InternalValidationResult.reject(err.toString())
        );
  }

  SafeFuture<InternalValidationResult> validate(DataColumnSidecar dataColumnSidecar);
}
