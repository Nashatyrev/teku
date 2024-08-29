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
