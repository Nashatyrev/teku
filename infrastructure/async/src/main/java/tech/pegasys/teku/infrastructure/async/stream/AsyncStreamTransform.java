/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.async.stream;

import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.CONTINUE;
import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.INCLUDE_AND_STOP;
import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.SKIP_AND_STOP;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface AsyncStreamTransform<T> extends BaseAsyncStreamTransform<T> {

  interface Slicer<T> extends BaseSlicer<T> {

    static <T> Slicer<T> limit(final long count) {
      return new Slicer<>() {
        private final AtomicLong remainCount = new AtomicLong(count);

        @Override
        public SliceResult slice(final T element) {
          return remainCount.decrementAndGet() > 0 ? CONTINUE : INCLUDE_AND_STOP;
        }
      };
    }

    static <T> Slicer<T> takeWhile(final Predicate<T> condition) {
      return t -> condition.test(t) ? CONTINUE : SKIP_AND_STOP;
    }

    default Slicer<T> then(final Slicer<T> nextSlicer) {
      return new Slicer<T>() {
        private boolean thisSlicerCompleted = false;

        @Override
        public SliceResult slice(final T element) {
          if (thisSlicerCompleted) {
            return nextSlicer.slice(element);
          } else {
            final SliceResult result = Slicer.this.slice(element);
            return switch (result) {
              case CONTINUE -> result;
              case SKIP_AND_STOP -> {
                thisSlicerCompleted = true;
                yield nextSlicer.slice(element);
              }
              case INCLUDE_AND_STOP -> {
                thisSlicerCompleted = true;
                yield CONTINUE;
              }
            };
          }
        }
      };
    }
  }

  // transformation

  // Suboptimal reference implementation
  // To be overridden in implementation with a faster variant
  default <R> AsyncStream<R> map(final Function<T, R> mapper) {
    return flatMap(t -> AsyncStream.of(mapper.apply(t)));
  }

  // Suboptimal reference implementation
  // To be overridden in implementation with a faster variant
  default AsyncStream<T> filter(final Predicate<T> filter) {
    return flatMap(t -> filter.test(t) ? AsyncStream.of(t) : AsyncStream.empty());
  }

  default AsyncStream<T> peek(final Consumer<T> visitor) {
    return map(
        t -> {
          visitor.accept(t);
          return t;
        });
  }

  default <R> AsyncStream<R> mapAsync(final Function<T, SafeFuture<R>> mapper) {
    return flatMap(e -> AsyncStream.create(mapper.apply(e)));
  }

  // slicing

  default AsyncStream<T> limit(final long count) {
    return slice(Slicer.limit(count));
  }

  default AsyncStream<T> takeWhile(final Predicate<T> whileCondition) {
    return slice(Slicer.takeWhile(whileCondition));
  }

  default AsyncStream<T> takeUntil(final Predicate<T> untilCondition, final boolean includeLast) {
    final Slicer<T> whileSlicer = Slicer.takeWhile(untilCondition.negate());
    final Slicer<T> untilSlicer = includeLast ? whileSlicer.then(Slicer.limit(1)) : whileSlicer;
    return slice(untilSlicer);
  }
}
