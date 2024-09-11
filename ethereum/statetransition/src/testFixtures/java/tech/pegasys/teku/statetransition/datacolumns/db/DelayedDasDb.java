package tech.pegasys.teku.statetransition.datacolumns.db;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
  private Duration delay;

  public DelayedDasDb(DataColumnSidecarDB delegate, AsyncRunner asyncRunner, Duration delay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.delay = delay;
  }

  public void setDelay(Duration delay) {
    this.delay = delay;
  }

  private <T> SafeFuture<T> delay(Supplier<SafeFuture<T>> futSupplier) {
    return asyncRunner.getDelayedFuture(delay).thenCompose(__ -> futSupplier.get());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delay(delegate::getFirstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delay(delegate::getFirstSamplerIncompleteSlot);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return delay(() -> delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(ColumnSlotAndIdentifier identifier) {
    return delay(() -> delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return delay(() -> delegate.getColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    return delay(() -> delegate.setFirstCustodyIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delay(() -> delegate.setFirstSamplerIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> addSidecar(DataColumnSidecar sidecar) {
    return delay(() -> delegate.addSidecar(sidecar));
  }

  @Override
  public SafeFuture<Void> pruneAllSidecars(UInt64 tillSlot) {
    return delay(() -> delegate.pruneAllSidecars(tillSlot));
  }
}
