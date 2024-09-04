package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.function.Predicate;

class FilteringIteratorCallback<T> extends AbstractDelegatingIteratorCallback<T, T> {

  private final Predicate<T> filter;

  protected FilteringIteratorCallback(AsyncIteratorCallback<T> delegate, Predicate<T> filter) {
    super(delegate);
    this.filter = filter;
  }

  @Override
  public SafeFuture<Boolean> onNext(T t) {
    if (filter.test(t)) {
      return delegate.onNext(t);
    } else {
      return TRUE_FUTURE;
    }
  }
}
