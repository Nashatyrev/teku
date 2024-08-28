/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class DataAndValidationResult<Data> {

  private final AvailabilityValidationResult validationResult;
  private final List<Data> dataList;
  private final Optional<Throwable> cause;

  public static <Data> DataAndValidationResult<Data> notAvailable() {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> notRequired() {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_REQUIRED, Collections.emptyList(), Optional.empty());
  }

  public static <Data> SafeFuture<DataAndValidationResult<Data>> notRequiredResultFuture() {
    return SafeFuture.completedFuture(notRequired());
  }

  public static <Data> DataAndValidationResult<Data> validResult(final List<Data> dataList) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.VALID, dataList, Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> invalidResult(final List<Data> dataList) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.INVALID, dataList, Optional.empty());
  }

  public static <Data> DataAndValidationResult<Data> invalidResult(
      final List<Data> dataList, final Throwable cause) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.INVALID, dataList, Optional.of(cause));
  }

  public static <Data> DataAndValidationResult<Data> notAvailable(final Throwable cause) {
    return new DataAndValidationResult<>(
        AvailabilityValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.of(cause));
  }

  DataAndValidationResult(
      final AvailabilityValidationResult validationResult,
      final List<Data> dataList,
      final Optional<Throwable> cause) {
    this.validationResult = validationResult;
    this.dataList = dataList;
    this.cause = cause;
  }

  public AvailabilityValidationResult getValidationResult() {
    return validationResult;
  }

  public List<Data> getData() {
    return dataList;
  }

  public Optional<Throwable> getCause() {
    return cause;
  }

  public boolean isValid() {
    return validationResult.equals(AvailabilityValidationResult.VALID);
  }

  public boolean isNotRequired() {
    return validationResult.equals(AvailabilityValidationResult.NOT_REQUIRED);
  }

  public boolean isInvalid() {
    return validationResult.equals(AvailabilityValidationResult.INVALID);
  }

  public boolean isNotAvailable() {
    return validationResult.equals(AvailabilityValidationResult.NOT_AVAILABLE);
  }

  public boolean isFailure() {
    return isInvalid() || isNotAvailable();
  }

  public boolean isSuccess() {
    return isValid() || isNotRequired();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DataAndValidationResult<?> that = (DataAndValidationResult<?>) o;
    return Objects.equals(validationResult, that.validationResult)
        && Objects.equals(dataList, that.dataList)
        && Objects.equals(cause, that.cause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validationResult, dataList, cause);
  }

  public String toLogString() {
    return MoreObjects.toStringHelper(this)
        .add("validationResult", validationResult)
        .add("dataListSize", dataList.size())
        .add("cause", cause)
        .toString();
  }
}
