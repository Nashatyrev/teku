package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

class FlattenIteratorCallback<TCol extends AsyncIterator<T>, T> extends AbstractDelegatingIteratorCallback<T, TCol> {

  protected FlattenIteratorCallback(AsyncIteratorCallback<T> delegate) {
    super(delegate);
  }

  @Override
  public SafeFuture<Boolean> onNext(TCol asyncIterator) {
    SafeFuture<Boolean> ret = new SafeFuture<>();
    asyncIterator.iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public SafeFuture<Boolean> onNext(T t) {
            SafeFuture<Boolean> proceedFuture = delegate.onNext(t);

            return proceedFuture.thenPeek(
                proceed -> {
                  if (!proceed) {
                    ret.complete(false);
                  }
                });
          }

          @Override
          public void onComplete() {
            ret.complete(true);
          }

          @Override
          public void onError(Throwable t) {
            ret.complete(false);
            delegate.onError(t);
          }
        });
    return ret;
  }
}
