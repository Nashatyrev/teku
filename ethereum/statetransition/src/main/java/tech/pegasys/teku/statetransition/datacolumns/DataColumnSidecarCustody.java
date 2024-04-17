package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.Optional;

public interface DataColumnSidecarCustody {

  void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar);

  SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(DataColumnIdentifier columnId);

}
