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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record CompleteIncompleteSlot(
    SlotColumnsTask firstIncomplete, SlotColumnsTask lastComplete) {
  static final CompleteIncompleteSlot ZERO = new CompleteIncompleteSlot(null, null);

  CompleteIncompleteSlot add(SlotColumnsTask slotColumnsTask) {
    if (firstIncomplete == null && slotColumnsTask.isIncomplete()) {
      return new CompleteIncompleteSlot(slotColumnsTask, lastComplete);
    } else if (slotColumnsTask.isComplete()) {
      return new CompleteIncompleteSlot(firstIncomplete, slotColumnsTask);
    } else {
      return this;
    }
  }

  Optional<UInt64> getFirstIncompleteSlot() {
    if (firstIncomplete != null) {
      return Optional.of(firstIncomplete.slot());
    } else if (lastComplete != null) {
      return Optional.of(lastComplete.slot().increment());
    } else {
      return Optional.empty();
    }
  }

  static <TElem, TAccum> Function<TElem, TAccum> scan(
      TAccum identity, BiFunction<TAccum, TElem, TAccum> combiner) {
    return new Function<>() {
      private TAccum curValue = identity;

      @Override
      public TAccum apply(TElem elem) {
        curValue = combiner.apply(curValue, elem);
        return curValue;
      }
    };
  }
}
