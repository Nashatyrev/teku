package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface AsyncStreamTransform<T> extends BaseAsyncStreamTransform<T> {

  interface Slicer<T> extends BaseSlicer<T> {

    static <T> Slicer<T> filter(Predicate<T> filter) {
      return t -> SliceResult.continueWith(filter.test(t));
    }

    static <T> Slicer<T> limit(long count) {
      return new Slicer<>() {
        private final AtomicLong remainCount = new AtomicLong(count);

        @Override
        public SliceResult slice(T element) {
          return remainCount.decrementAndGet() > 0
              ? SliceResult.INCLUDE_AND_CONTINUE
              : SliceResult.INCLUDE_AND_STOP;
        }
      };
    }

    static <T> Slicer<T> takeWhile(Predicate<T> condition) {
      return t -> condition.test(t) ? SliceResult.INCLUDE_AND_CONTINUE : SliceResult.SKIP_AND_STOP;
    }

    default Slicer<T> then(Slicer<T> nextSlicer) {
      return new Slicer<T>() {
        private boolean thisSlicerCompleted = false;

        @Override
        public SliceResult slice(T element) {
          if (thisSlicerCompleted) {
            return nextSlicer.slice(element);
          } else {
            SliceResult result = Slicer.this.slice(element);
            if (result.isComplete()) {
              thisSlicerCompleted = true;
              return result.toContinue();
            } else {
              return result;
            }
          }
        }
      };
    }
  }

  // Suboptimal reference implementation
  // To be overridden in implementation with a faster variant
  default <R> AsyncStream<R> map(Function<T, R> mapper) {
    return flatMap(t -> AsyncStream.of(mapper.apply(t)));
  }

  default AsyncStream<T> peek(Consumer<T> visitor) {
    return map(
        t -> {
          visitor.accept(t);
          return t;
        });
  }

  default <R> AsyncStream<R> mapAsync(Function<T, SafeFuture<R>> mapper) {
    return flatMap(e -> AsyncStream.create(mapper.apply(e)));
  }

  default AsyncStream<T> filter(Predicate<T> filter) {
    return slice(Slicer.filter(filter));
  }

  default AsyncStream<T> limit(long count) {
    return slice(Slicer.limit(count));
  }
}
