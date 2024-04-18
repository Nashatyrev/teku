package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.Optional;
import java.util.stream.Stream;

public interface DataColumnSidecarDB {

  // read

  Optional<UInt64> getFirstIncompleteSlot();

  Optional<DataColumnSidecar> getSidecar(DataColumnIdentifier identifier);

  Stream<DataColumnIdentifier> streamColumnIdentifiers(UInt64 slot);

  // update

  void setFirstIncompleteSlot(UInt64 slot);

  void addSidecar(DataColumnSidecar sidecar);

  void pruneAllSidecars(UInt64 tillSlot);
}
