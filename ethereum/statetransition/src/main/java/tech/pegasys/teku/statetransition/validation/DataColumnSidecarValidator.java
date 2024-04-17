package tech.pegasys.teku.statetransition.validation;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;

/**
 * Check the DataColumnSidecar strict validity received either via Pubsub or Req/Resp
 */
public interface DataColumnSidecarValidator {

  public static DataColumnSidecarValidator NOOP = sidecar -> SafeFuture.COMPLETE;

  public static DataColumnSidecarValidator create() {
    // TODO
    return NOOP;
  }

  SafeFuture<Void> validate(DataColumnSidecar sidecar);
}
