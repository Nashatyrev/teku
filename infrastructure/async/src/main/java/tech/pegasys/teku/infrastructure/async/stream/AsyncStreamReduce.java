package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface AsyncStreamReduce<T> extends BaseAsyncStreamReduce<T>, AsyncStreamTransform<T> {

  default SafeFuture<Optional<T>> findFirst() {
    return this.limit(1)
        .toList()
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }

  default SafeFuture<Void> forEach(Consumer<T> consumer) {
    return collect(Collector.of(() -> null, (a, t) -> consumer.accept(t), noBinaryOperator()));
  }

  default <C extends Collection<T>> SafeFuture<C> collect(C targetCollection) {
    return collect(Collectors.toCollection(() -> targetCollection));
  }

  default SafeFuture<List<T>> toList() {
    return collect(Collectors.toUnmodifiableList());
  }

  default SafeFuture<Optional<T>> findLast() {
    return collectLast(1)
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }


  default SafeFuture<List<T>> collectLast(int count) {
    class CircularBuf<C> {
      final ArrayDeque<C> buf;
      final int maxSize;

      public CircularBuf(int maxSize) {
        buf = new ArrayDeque<>(maxSize);
        this.maxSize = maxSize;
      }

      public void add(C t) {
        if (buf.size() == maxSize) {
          buf.removeFirst();
        }
        buf.add(t);
      }
    }
    return collect(
        Collector.of(
            () -> new CircularBuf<T>(count),
            CircularBuf::add,
            noBinaryOperator(),
            buf -> buf.buf.stream().toList()));
  }

  private static <C> BinaryOperator<C> noBinaryOperator() {
    return (c, c2) -> {
      throw new UnsupportedOperationException();
    };
  }
}
