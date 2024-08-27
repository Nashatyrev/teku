package tech.pegasys.teku.statetransition.datacolumns.util.rx;

abstract class OperationAsyncIterator<S, T> implements AsyncIterator<T> {
  private final AsyncIterator<S> delegateIterator;

  public OperationAsyncIterator(AsyncIterator<S> delegateIterator) {
    this.delegateIterator = delegateIterator;
  }

  protected abstract AsyncIteratorCallback<S> createDelegateCallback(
      AsyncIteratorCallback<T> sourceCallback);

  @Override
  public void iterate(AsyncIteratorCallback<T> callback) {
    delegateIterator.iterate(createDelegateCallback(callback));
  }
}
