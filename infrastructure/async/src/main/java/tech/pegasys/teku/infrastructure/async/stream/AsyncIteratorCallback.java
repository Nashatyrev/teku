package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

interface AsyncIteratorCallback<T> {

  SafeFuture<Boolean> TRUE_FUTURE = SafeFuture.completedFuture(true);
  SafeFuture<Boolean> FALSE_FUTURE = SafeFuture.completedFuture(false);

  SafeFuture<Boolean> onNext(T t);

  void onComplete();

  void onError(Throwable t);
}
