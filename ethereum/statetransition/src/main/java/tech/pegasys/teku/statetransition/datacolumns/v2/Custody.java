package tech.pegasys.teku.statetransition.datacolumns.v2;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

import java.util.Optional;

public interface Custody {

  SafeFuture<Optional<DataColumnSidecar>> getColumn(DataColumnSlotAndIdentifier columnId);

  SafeFuture<Boolean> hasColumn(DataColumnSlotAndIdentifier columnId);
}
