package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AwaitIterator<T> implements AsyncIterator<T>{
  private final SafeFuture<AsyncIterator<T>> iteratorPromise;

  public AwaitIterator(SafeFuture<AsyncIterator<T>> iteratorPromise) {
    this.iteratorPromise = iteratorPromise;
  }

  @Override
  public void iterate(AsyncIteratorCallback<T> callback) {
    iteratorPromise.finish(
        iterator -> iterator.iterate(callback),
        callback::onError
    );
  }
}
