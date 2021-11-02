/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.async;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class OptionalFuture<C> extends SafeFuture<Optional<C>> {

  public static <C> OptionalFuture<C> of1(CompletionStage<Optional<C>> val) {
    throw new UnsupportedOperationException();
  }

  public static <C> OptionalFuture<C> completedFuture(Optional<C> val) {
    throw new UnsupportedOperationException();
  }

  public abstract <V> OptionalFuture<V> map(Function<C, V> mapper);

  public abstract <V> OptionalFuture<V> composeMap(
      Function<C, ? extends CompletionStage<V>> mapper);

  public abstract <V> OptionalFuture<V> flatMap(Function<C, Optional<V>> mapper);

  @Override
  public OptionalFuture<C> catchAndRethrow(ExceptionThrowingConsumer<Throwable> onError) {
    return super.catchAndRethrow(onError);
  }

  public abstract OptionalFuture<C> thenPeekExisting(Consumer<C> consumer);

  public abstract OptionalFuture<C> thenPeekAbsent(Runnable consumer);

  public abstract SafeFuture<Void> thenAccept(
      Consumer<C> presentValueConsumer, Runnable absentRunner);

  public abstract SafeFuture<C> orElseThrow();

  public abstract SafeFuture<C> orElse(Supplier<? extends C> val);

  public abstract SafeFuture<C> orElseCompose(Supplier<? extends CompletionStage<C>> val);
}
