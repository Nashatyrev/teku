package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import java.util.function.Predicate;

public class FilteringIteratorCallback<T> extends AbstractDelegatingIteratorCallback<T, T> {

  private final Predicate<T> filter;

  protected FilteringIteratorCallback(AsyncIteratorCallback<T> delegate, Predicate<T> filter) {
    super(delegate);
    this.filter = filter;
  }

  @Override
  public boolean onNext(T t) {
    if (filter.test(t)) {
      return delegate.onNext(t);
    } else {
      return true;
    }
  }
}
