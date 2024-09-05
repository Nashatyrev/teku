package tech.pegasys.teku.infrastructure.async.stream;

import java.util.function.Function;

public interface BaseAsyncStreamTransform<T> {

  enum SliceResult {
    CONTINUE,
    INCLUDE_AND_STOP,
    SKIP_AND_STOP
  }

  interface BaseSlicer<T> {
    SliceResult slice(T element);
  }

  <R> AsyncStream<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper);

  AsyncStream<T> slice(BaseSlicer<T> slicer);
}
