package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.List;
import java.util.Optional;

abstract class AbstractDelegatingDasDb implements DataColumnSidecarDB {
  protected final DataColumnSidecarDB delegateDb;

  public AbstractDelegatingDasDb(DataColumnSidecarDB delegateDb) {
    this.delegateDb = delegateDb;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegateDb.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delegateDb.getFirstSamplerIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return delegateDb.getSidecar(identifier);
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return delegateDb.getColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    return delegateDb.setFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delegateDb.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    delegateDb.addSidecar(sidecar);
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    delegateDb.pruneAllSidecars(tillSlot);
  }
}
