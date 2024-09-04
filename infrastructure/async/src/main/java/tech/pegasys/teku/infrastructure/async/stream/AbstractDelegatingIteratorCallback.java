package tech.pegasys.teku.infrastructure.async.stream;

abstract class AbstractDelegatingIteratorCallback<S, T> implements AsyncIteratorCallback<T> {

  protected final AsyncIteratorCallback<S> delegate;

  protected AbstractDelegatingIteratorCallback(AsyncIteratorCallback<S> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onComplete() {
    delegate.onComplete();
  }

  @Override
  public void onError(Throwable t) {
    delegate.onError(t);
  }
}
