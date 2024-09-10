package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class DelayedDasDb implements DataColumnSidecarDB {
  private final DataColumnSidecarDB delegate;
  private final AsyncRunner asyncRunner;
  private final Duration delay;

  public DelayedDasDb(DataColumnSidecarDB delegate, AsyncRunner asyncRunner, Duration delay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.delay = delay;
  }

  private <T> SafeFuture<T> delay(SafeFuture<T> fut) {
    return fut.thenCompose(r -> asyncRunner.getDelayedFuture(delay).thenApply(__ -> r));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delay(delegate.getFirstCustodyIncompleteSlot());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delay(delegate.getFirstSamplerIncompleteSlot());
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return delay(delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(ColumnSlotAndIdentifier identifier) {
    return delay(delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return delay(delegate.getColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    return delay(delegate.setFirstCustodyIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delay(delegate.setFirstSamplerIncompleteSlot(slot));
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    delegate.addSidecar(sidecar);
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    delegate.pruneAllSidecars(tillSlot);
  }
}
