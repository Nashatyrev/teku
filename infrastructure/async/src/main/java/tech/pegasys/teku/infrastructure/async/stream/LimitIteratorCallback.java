package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.concurrent.atomic.AtomicLong;

class LimitIteratorCallback<T> extends AbstractDelegatingIteratorCallback<T, T> {

  private final AtomicLong limit;

  protected LimitIteratorCallback(AsyncIteratorCallback<T> delegate, long limit) {
    super(delegate);
    this.limit = new AtomicLong(limit);
  }

  @Override
  public SafeFuture<Boolean> onNext(T t) {
    if (limit.getAndDecrement() > 0) {
      return delegate.onNext(t);
    } else {
      return FALSE_FUTURE;
    }
  }
}
