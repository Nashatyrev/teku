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

import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public interface FinalizedSlotListener {

  interface Subscriber {

    static Subscriber create(Spec spec, EventChannels eventChannels, UInt64 currentFinalizedEpoch) {
      return listener -> {
        eventChannels.subscribe(
            FinalizedCheckpointChannel.class,
            (checkpoint, fromOptimisticBlock) -> {
              UInt64 epoch = checkpoint.getEpoch();
              listener.onNewFinalizedSlot(epoch.times(spec.atEpoch(epoch).getSlotsPerEpoch()));
            });
        return currentFinalizedEpoch.times(spec.atEpoch(currentFinalizedEpoch).getSlotsPerEpoch());
      };
    }

    UInt64 subscribeToFinalizedSlots(FinalizedSlotListener listener);
  }

  /**
   * Should be first called with the current finalized slot on startup and then called whenever a
   * new slot is finalized
   */
  void onNewFinalizedSlot(UInt64 newFinalizedSlot);
}
