package tech.pegasys.teku.statetransition.datacolumns.v2;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public interface AllColumns {

  enum ColumnSource {
    /**
     * Received from a gossip data_column_subnet_# topic
     */
    GOSSIP,
    /**
     * Was explicitly requested from a peer via Req/Resp
     */
    REQ_RESP,
    /**
     * Was created locally by Validator Client
     */
    PUBLISH,
    /**
     * Was restored from existing custody columns
     */
    RECOVER_PASSIVE,
    /**
     * Was restored by actively requesting other columns
     */
    RECOVER_ACTIVE,
    /**
     * Retrieved from the Execution Engine
     */
    EE_TX_POOL
  }


  SafeFuture<DataColumnSidecar> awaitColumn(DataColumnSlotAndIdentifier columnId);

  SafeFuture<Void> awaitHasColumn(DataColumnSlotAndIdentifier columnId);
}

