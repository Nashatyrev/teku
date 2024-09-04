package tech.pegasys.teku.infrastructure.async.stream;

import java.util.concurrent.CompletionStage;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

class FutureAsyncIteratorImpl<T> extends AsyncIterator<T> {

  private final SafeFuture<T> future;

  FutureAsyncIteratorImpl(CompletionStage<T> future) {
    this.future = SafeFuture.of(future);
  }

  @Override
  public void iterate(AsyncIteratorCallback<T> callback) {
    future.finish(
        succ -> {
          callback.onNext(succ).ifExceptionGetsHereRaiseABug();
          callback.onComplete();
        },
        callback::onError);
  }
}
