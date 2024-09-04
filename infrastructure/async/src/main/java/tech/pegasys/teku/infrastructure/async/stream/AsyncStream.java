package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Similar to {@link java.util.stream.Stream} but may perform async operations
 */
public interface AsyncStream<T> {

  // builders

  @SafeVarargs
  static <T> AsyncStream<T> of(T ... elements) {
    return create(List.of(elements).iterator());
  }

  static <T> AsyncStream<T> create(Iterator<T> iterator) {
    return new SyncToAsyncIteratorImpl<>(iterator);
  }

  static <T> AsyncStream<T> create(CompletionStage<T> future) {
    return new FutureAsyncIteratorImpl<>(future);
  }

  // transformations

  AsyncStream<T> filter(Predicate<T> filter);

  AsyncStream<T> limit(long limit);

  <R> AsyncStream<R> map(Function<T, R> mapper);

  <R> AsyncStream<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper);

  // terminal operators

  SafeFuture<Void> forEach(Consumer<T> consumer);

  <C extends Collection<T>> SafeFuture<C> collect(C targetCollection);

  default SafeFuture<List<T>> toList() {
    return collect(new ArrayList<>());
  }
}
