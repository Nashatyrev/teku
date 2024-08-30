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

  static <T> AsyncIterator<T> empty() {
    return AsyncIteratorCallback::onComplete;
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
        return new MapIteratorCallback<>(
            new FlattenIteratorCallback<>(sourceCallback), toCollectionMapper);
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
