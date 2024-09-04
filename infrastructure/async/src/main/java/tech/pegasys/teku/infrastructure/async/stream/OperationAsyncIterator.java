package tech.pegasys.teku.infrastructure.async.stream;

import java.util.function.Function;

abstract class OperationAsyncIterator<S, T> extends AsyncIterator<T> {
  private final AsyncIterator<S> delegateIterator;

  public static <S, T> AsyncIterator<T> create(
      AsyncIterator<S> srcIterator,
      Function<AsyncIteratorCallback<T>, AsyncIteratorCallback<S>> callbackMapper) {
    return new OperationAsyncIterator<>(srcIterator) {
      @Override
      protected AsyncIteratorCallback<S> createDelegateCallback(
          AsyncIteratorCallback<T> sourceCallback) {
        return callbackMapper.apply(sourceCallback);
      }
    };
  }

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
