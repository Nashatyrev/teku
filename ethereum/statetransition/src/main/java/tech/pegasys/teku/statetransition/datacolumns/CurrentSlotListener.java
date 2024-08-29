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
