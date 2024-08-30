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

import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface CurrentSlotListener {

  interface Subscriber {

    static Subscriber create(EventChannels eventChannels, UInt64 currentSlot) {
      return new Subscriber() {
        private volatile UInt64 lastSlot = currentSlot;

        @Override
        public UInt64 subscribeCurrentSlotUpdates(CurrentSlotListener listener) {
          eventChannels.subscribe(
              SlotEventsChannel.class,
              slot -> {
                if (slot.isGreaterThan(lastSlot)) {
                  lastSlot = slot;
                  listener.onCurrentSlot(slot);
                }
              });
          return currentSlot;
        }
      };
    }

    /**
     * On subscribe the listener should be synchronously called with the current slot
     *
     * @return current slot in effect
     */
    UInt64 subscribeCurrentSlotUpdates(CurrentSlotListener listener);
  }

  interface Supplier {

    static Supplier createFromSubscriber(Subscriber subscriber) {
      return new Supplier() {
        private volatile UInt64 currentSlot =
            subscriber.subscribeCurrentSlotUpdates(this::onSlotUpdate);

        @Override
        public UInt64 getCurrentSlot() {
          return currentSlot;
        }

        private void onSlotUpdate(UInt64 newSlot) {
          currentSlot = newSlot;
        }
      };
    }

    UInt64 getCurrentSlot();
  }

  void onCurrentSlot(UInt64 currentSlot);
}
