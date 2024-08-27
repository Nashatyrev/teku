package tech.pegasys.teku.statetransition.datacolumns.util.rx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface AsyncIterator<T> {

  static <T> AsyncIterator<T> awaitIterator(SafeFuture<AsyncIterator<T>> iteratorPromise) {
    return new AwaitIterator<>(iteratorPromise);
  }

  static <T> AsyncIterator<T> createOneShot(Iterator<SafeFuture<T>> iterator) {
    return new OneShotAsyncIteratorImpl<>(iterator);
  }

  void iterate(AsyncIteratorCallback<T> callback);

  default AsyncIterator<T> filter(Predicate<T> filter) {
    return new OperationAsyncIterator<>(this) {
      @Override
      protected AsyncIteratorCallback<T> createDelegateCallback(
          AsyncIteratorCallback<T> sourceCallback) {
        return new FilteringIteratorCallback<>(sourceCallback, filter);
      }
    };
  }

  default AsyncIterator<T> limit(long limit) {
    return new OperationAsyncIterator<>(this) {
      @Override
      protected AsyncIteratorCallback<T> createDelegateCallback(
          AsyncIteratorCallback<T> sourceCallback) {
        return new LimitIteratorCallback<>(sourceCallback, limit);
      }
    };
  }

  default <T1> AsyncIterator<T1> map(Function<T, T1> mapper) {
    return new OperationAsyncIterator<>(this) {
      @Override
      protected AsyncIteratorCallback<T> createDelegateCallback(
          AsyncIteratorCallback<T1> sourceCallback) {
        return new MapIteratorCallback<>(sourceCallback, mapper);
      }
    };
  }

  default <T1> AsyncIterator<T1> flatten(Function<T, Iterable<T1>> toCollectionMapper) {
    return new OperationAsyncIterator<>(this) {
      @Override
      protected AsyncIteratorCallback<T> createDelegateCallback(
          AsyncIteratorCallback<T1> sourceCallback) {
        return new MapIteratorCallback<>(new FlattenIteratorCallback<>(sourceCallback), toCollectionMapper);
      }
    };
  }

  default <C extends Collection<T>> SafeFuture<C> collect(C targetCollection) {
    return new AsyncIteratorCollector<>(this).collect(targetCollection);
  }

  default SafeFuture<List<T>> collectToList() {
    return new AsyncIteratorCollector<>(this).collect(new ArrayList<>());
  }
}


