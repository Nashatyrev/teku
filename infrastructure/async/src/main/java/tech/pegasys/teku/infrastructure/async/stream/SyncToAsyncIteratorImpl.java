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

import java.util.Iterator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class SyncToAsyncIteratorImpl<T> extends AsyncIterator<T> {

  private final Iterator<T> iterator;
  private AsyncIteratorCallback<T> callback;

  SyncToAsyncIteratorImpl(final Iterator<T> iterator) {
    this.iterator = iterator;
  }

  @Override
  public void iterate(final AsyncIteratorCallback<T> callback) {
    synchronized (this) {
      if (this.callback != null) {
        throw new IllegalStateException("This one-shot iterator has been used already");
      }
      this.callback = callback;
    }
    next();
  }

  private void next() {
    try {
      while (true) {
        if (!iterator.hasNext()) {
          callback.onComplete();
          break;
        }
        final T next = iterator.next();
        final SafeFuture<Boolean> shouldContinueFut = callback.onNext(next);
        if (shouldContinueFut.isCompletedNormally()) {
          final Boolean shouldContinue = shouldContinueFut.getImmediately();
          if (!shouldContinue) {
            callback.onComplete();
            break;
          }
        } else {
          shouldContinueFut.finish(this::onNextComplete, err -> callback.onError(err));
          break;
        }
      }
    } catch (Throwable e) {
      callback.onError(e);
    }
  }

  private void onNextComplete(final boolean shouldContinue) {
    if (shouldContinue) {
      next();
    } else {
      callback.onComplete();
    }
  }
}
