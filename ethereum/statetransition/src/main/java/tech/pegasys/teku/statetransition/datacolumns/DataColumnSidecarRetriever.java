package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

/**
 * The class which searches for a specific {@link DataColumnSidecar} across nodes in the network
 */
public interface DataColumnSidecarRetriever {

  /**
   * Queues the specified sidecar for search
   * @return a future which may run indefinitely until finds a requested data or cancelled
   */
  SafeFuture<DataColumnSidecar> retrieve(DataColumnIdentifier columnId);
}
