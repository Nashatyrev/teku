/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class OperationEpochValidator<T> implements OperationValidator<T> {

  private final UInt64 startEpoch;
  private final UInt64 endEpoch;
  private final Function<T, UInt64> getEpochForMessage;

  public OperationEpochValidator(
      final UInt64 startEpoch,
      final UInt64 endEpoch,
      final Function<T, UInt64> getEpochForMessage) {
    this.startEpoch = startEpoch;
    this.endEpoch = endEpoch;
    this.getEpochForMessage = getEpochForMessage;
  }

  @Override
  public boolean isValid(final T message) {
    final UInt64 messageEpoch = getEpochForMessage.apply(message);
    return messageEpoch.isGreaterThanOrEqualTo(startEpoch)
        && messageEpoch.isLessThanOrEqualTo(endEpoch);
  }
}
