package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.stream.Collector;

public interface BaseAsyncStreamReduce<T> {

  <A, R> SafeFuture<R> collect(Collector<T, A, R> collector);
}
