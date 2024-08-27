package tech.pegasys.teku.statetransition.datacolumns.util.rx;

public class FlattenIteratorCallback<TCol extends Iterable<T>, T> extends AbstractDelegatingIteratorCallback<T, TCol> {

  protected FlattenIteratorCallback(AsyncIteratorCallback<T> delegate) {
    super(delegate);
  }

  @Override
  public boolean onNext(TCol t) {
    for (T elem : t) {
      if (!delegate.onNext(elem)) {
        return false;
      };
    }
    return true;
  }
}
