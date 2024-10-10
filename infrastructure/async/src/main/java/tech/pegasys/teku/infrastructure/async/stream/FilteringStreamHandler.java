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

import java.util.function.Predicate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class FilteringStreamHandler<T> extends AbstractDelegatingStreamHandler<T, T> {

  private final Predicate<T> filter;

  protected FilteringStreamHandler(AsyncStreamHandler<T> delegate, Predicate<T> filter) {
    super(delegate);
    this.filter = filter;
  }

  @Override
  public SafeFuture<Boolean> onNext(T t) {
    if (filter.test(t)) {
      return delegate.onNext(t);
    } else {
      return TRUE_FUTURE;
    }
  }
}