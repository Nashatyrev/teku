package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.function.Function;

class MapIteratorCallback<T, S> extends AbstractDelegatingIteratorCallback<T, S> {

  private final Function<S, T> mapper;

  protected MapIteratorCallback(AsyncIteratorCallback<T> delegate, Function<S, T> mapper) {
    super(delegate);
    this.mapper = mapper;
  }

  @Override
  public SafeFuture<Boolean> onNext(S s) {
    try {
      return delegate.onNext(mapper.apply(s));
    } catch (Exception e) {
      delegate.onError(e);
      return FALSE_FUTURE;
    }
  }
}
