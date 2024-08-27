package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import java.util.concurrent.atomic.AtomicLong;

class LimitIteratorCallback<T> extends AbstractDelegatingIteratorCallback<T, T> {

  private final AtomicLong limit;


  protected LimitIteratorCallback(AsyncIteratorCallback<T> delegate, long limit) {
    super(delegate);
    this.limit = new AtomicLong(limit);
  }

  @Override
  public boolean onNext(T t) {
    if (limit.getAndDecrement() > 0) {
      return delegate.onNext(t);
    } else {
      return false;
    }
  }
}
