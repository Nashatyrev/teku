package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.Collection;

class AsyncIteratorCollector<T> {

  private final AsyncIterator<T> iterator;

  public AsyncIteratorCollector(AsyncIterator<T> iterator) {
    this.iterator = iterator;
  }

  public <C extends Collection<T>> SafeFuture<C> collect(C collection) {
    SafeFuture<C> promise = new SafeFuture<>();
    iterator.iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public boolean onNext(T t) {
            synchronized (collection) {
              collection.add(t);
            }
            return true;
          }

          @Override
          public void onComplete() {
            promise.complete(collection);
          }

          @Override
          public void onError(Throwable t) {
            promise.completeExceptionally(t);
          }
        });
    return promise;
  }
}
