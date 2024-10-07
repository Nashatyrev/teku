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

<<<<<<<< HEAD:ethereum/spec/src/testFixtures/java/tech/pegasys/teku/spec/propertytest/suppliers/execution/versions/electra/DepositRequestSupplier.java
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositRequestSupplier extends DataStructureUtilSupplier<DepositRequest> {

  public DepositRequestSupplier() {
    super(DataStructureUtil::randomDepositRequest, SpecMilestone.ELECTRA);
========
import java.util.function.Function;

/**
 * Contains fundamental transformation stream methods All other transformations are expressed my
 * means of those methods
 */
public interface BaseAsyncStreamTransform<T> {

  enum SliceResult {
    CONTINUE,
    INCLUDE_AND_STOP,
    SKIP_AND_STOP
>>>>>>>> das:infrastructure/async/src/main/java/tech/pegasys/teku/infrastructure/async/stream/BaseAsyncStreamTransform.java
  }

  interface BaseSlicer<T> {
    SliceResult slice(T element);
  }

  <R> AsyncStream<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper);

  AsyncStream<T> slice(BaseSlicer<T> slicer);
}
