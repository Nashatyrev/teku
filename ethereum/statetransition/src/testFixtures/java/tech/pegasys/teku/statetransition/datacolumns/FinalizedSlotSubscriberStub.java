package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FinalizedSlotSubscriberStub implements FinalizedSlotListener.Subscriber {

  private volatile UInt64 finalizedSlot;
  private final List<FinalizedSlotListener> listeners = new CopyOnWriteArrayList<>();

  public FinalizedSlotSubscriberStub(UInt64 finalizedSlot) {
    this.finalizedSlot = finalizedSlot;
  }

  public void setFinalizedSlot(UInt64 finalizedSlot) {
    this.finalizedSlot = finalizedSlot;
    listeners.forEach(l -> l.onNewFinalizedSlot(finalizedSlot));
  }

  @Override
  public UInt64 subscribeToFinalizedSlots(FinalizedSlotListener listener) {
    listeners.add(listener);
    return finalizedSlot;
  }
}
