package tech.pegasys.teku.infrastructure.async.stream;

import java.util.Iterator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class SyncToAsyncIteratorImpl<T> extends AsyncIterator<T> {

  private final Iterator<T> iterator;
  private AsyncIteratorCallback<T> callback;

  SyncToAsyncIteratorImpl(Iterator<T> iterator) {
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
      while (true) {
        if (!iterator.hasNext()) {
          callback.onComplete();
          break;
        }
        T next = iterator.next();
        SafeFuture<Boolean> shouldContinueFut = callback.onNext(next);
        if (shouldContinueFut.isCompletedNormally()) {
          Boolean shouldContinue = shouldContinueFut.getImmediately();
          if (!shouldContinue) {
            callback.onComplete();
            break;
          }
        } else {
          shouldContinueFut.finish(this::onNextComplete, err -> callback.onError(err));
          break;
        }
      }
    } catch (Throwable e) {
      callback.onError(e);
    }
  }

  private void onNextComplete(boolean shouldContinue) {
    if (shouldContinue) {
      next();
    } else {
      callback.onComplete();
    }
  }
}
