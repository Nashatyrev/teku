package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.Iterator;

class OneShotAsyncIteratorImpl<T> implements AsyncIterator<T> {

  private final Iterator<SafeFuture<T>> iterator;
  private AsyncIteratorCallback<T> callback;

  OneShotAsyncIteratorImpl(Iterator<SafeFuture<T>> iterator) {
    this.iterator = iterator;
  }

  @Override
  public void iterate(AsyncIteratorCallback<T> callback) {
    synchronized (this) {
      if (this.callback != null) {
        throw new IllegalStateException("This one-shot iterator has been used already");
      }
      this.callback = callback;
    }
    next();
  }

  private void next() {
    try {
      if (!iterator.hasNext()) {
        callback.onComplete();
      } else {
        SafeFuture<T> nextPromise = iterator.next();
        nextPromise
            .thenAccept(
                o -> {
                  boolean shouldContinue = callback.onNext(o);
                  if (shouldContinue) {
                    next();
                  } else {
                    callback.onComplete();
                  }
                })
            .finish(err -> callback.onError(err));
      }
    } catch (Throwable e) {
      callback.onError(e);
    }
  }
}
