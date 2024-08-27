package tech.pegasys.teku.statetransition.datacolumns.util.rx;

public abstract class AbstractDelegatingIteratorCallback<S, T> implements AsyncIteratorCallback<T> {

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
