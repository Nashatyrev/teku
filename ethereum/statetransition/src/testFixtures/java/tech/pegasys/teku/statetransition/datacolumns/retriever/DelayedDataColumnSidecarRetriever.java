package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.time.Duration;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSlotAndIdentifier;

public class DelayedDataColumnSidecarRetriever implements DataColumnSidecarRetriever {
  private final DataColumnSidecarRetriever delegate;
  private final AsyncRunner asyncRunner;
  private Duration delay;

  public DelayedDataColumnSidecarRetriever(DataColumnSidecarRetriever delegate, AsyncRunner asyncRunner, Duration delay) {
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
  public SafeFuture<DataColumnSidecar> retrieve(DataColumnSlotAndIdentifier columnId) {
    return delay(() -> delegate.retrieve(columnId));
  }
}
