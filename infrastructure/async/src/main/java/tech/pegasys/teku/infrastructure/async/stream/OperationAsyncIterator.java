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

import java.util.function.Function;

abstract class OperationAsyncIterator<S, T> extends AsyncIterator<T> {
  private final AsyncIterator<S> delegateIterator;

  public static <S, T> AsyncIterator<T> create(
      AsyncIterator<S> srcIterator,
      Function<AsyncIteratorCallback<T>, AsyncIteratorCallback<S>> callbackMapper) {
    return new OperationAsyncIterator<>(srcIterator) {
      @Override
      protected AsyncIteratorCallback<S> createDelegateCallback(
          AsyncIteratorCallback<T> sourceCallback) {
        return callbackMapper.apply(sourceCallback);
      }
    };
  }

  public OperationAsyncIterator(AsyncIterator<S> delegateIterator) {
    this.delegateIterator = delegateIterator;
  }

  protected abstract AsyncIteratorCallback<S> createDelegateCallback(
      AsyncIteratorCallback<T> sourceCallback);

  @Override
  public void iterate(AsyncIteratorCallback<T> callback) {
    delegateIterator.iterate(createDelegateCallback(callback));
  }
}