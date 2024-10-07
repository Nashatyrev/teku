package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

interface AsyncQueue<T> {

  void put(T item);

  SafeFuture<T> take();
}
