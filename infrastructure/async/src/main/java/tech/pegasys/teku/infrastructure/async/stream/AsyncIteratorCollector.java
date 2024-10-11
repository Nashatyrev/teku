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

import java.util.stream.Collector;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class AsyncIteratorCollector<T> {

  private final AsyncIterator<T> iterator;

  public AsyncIteratorCollector(final AsyncIterator<T> iterator) {
    this.iterator = iterator;
  }

  public <R, A> SafeFuture<R> collect(final Collector<T, A, R> collector) {
    final SafeFuture<R> promise = new SafeFuture<>();
    final A accumulator = collector.supplier().get();
    iterator.iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public SafeFuture<Boolean> onNext(final T t) {
            collector.accumulator().accept(accumulator, t);
            return TRUE_FUTURE;
          }

          @Override
          public void onComplete() {
            final R result = collector.finisher().apply(accumulator);
            promise.complete(result);
          }

          @Override
          public void onError(final Throwable t) {
            promise.completeExceptionally(t);
          }
        });
    return promise;
  }
}
