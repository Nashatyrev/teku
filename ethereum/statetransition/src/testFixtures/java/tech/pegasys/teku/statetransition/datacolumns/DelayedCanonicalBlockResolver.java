package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public class DelayedCanonicalBlockResolver implements CanonicalBlockResolver {

  private final CanonicalBlockResolver delegate;
  private final AsyncRunner asyncRunner;
  private Duration delay;

  public DelayedCanonicalBlockResolver(CanonicalBlockResolver delegate, AsyncRunner asyncRunner, Duration delay) {
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
  public SafeFuture<Optional<BeaconBlock>> getBlockAtSlot(UInt64 slot) {
    return delay(() -> delegate.getBlockAtSlot(slot));
  }
}
