package tech.pegasys.teku.infrastructure.async.stream;

import java.util.function.Function;

public interface BaseAsyncStreamTransform<T> {

  enum SliceResult {
    INCLUDE_AND_CONTINUE(true, false),
    INCLUDE_AND_STOP(true, true),
    SKIP_AND_CONTINUE(false, false),
    SKIP_AND_STOP(false, true),
    ;
    static SliceResult continueWith(boolean include) {
      return include ? INCLUDE_AND_CONTINUE : SKIP_AND_CONTINUE;
    }


    private final boolean include;
    private final boolean complete;

    SliceResult(boolean include, boolean complete) {
      this.include = include;
      this.complete = complete;
    }

    public boolean isInclude() {
      return include;
    }

    public boolean isComplete() {
      return complete;
    }

    public SliceResult toContinue() {
      return switch (this) {
        case INCLUDE_AND_CONTINUE -> INCLUDE_AND_CONTINUE;
        case INCLUDE_AND_STOP -> INCLUDE_AND_CONTINUE;
        case SKIP_AND_CONTINUE -> SKIP_AND_CONTINUE;
        case SKIP_AND_STOP -> SKIP_AND_CONTINUE;
      };
    }
  }

  interface BaseSlicer<T> {
    SliceResult slice(T element);
  }

  <R> AsyncStream<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper);

  AsyncStream<T> slice(BaseSlicer<T> slicer);
}
