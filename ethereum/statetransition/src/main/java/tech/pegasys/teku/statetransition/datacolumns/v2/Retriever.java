package tech.pegasys.teku.statetransition.datacolumns.v2;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public interface Retriever {

  SafeFuture<DataColumnSidecar> retrieve(DataColumnSlotAndIdentifier columnId);
}
