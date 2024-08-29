package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CurrentSlotSubscriberStub implements CurrentSlotListener.Subscriber {
  private volatile UInt64 currentSlot;
  private final List<CurrentSlotListener> listeners = new CopyOnWriteArrayList<>();

  public CurrentSlotSubscriberStub(UInt64 currentSlot) {
    this.currentSlot = currentSlot;
  }

  public void setCurrentSlot(UInt64 currentSlot) {
    this.currentSlot = currentSlot;
    listeners.forEach(l -> l.onCurrentSlot(currentSlot));
  }

  public UInt64 getCurrentSlot() {
    return currentSlot;
  }

  @Override
  public UInt64 subscribeCurrentSlotUpdates(CurrentSlotListener listener) {
    listeners.add(listener);
    return currentSlot;
  }
}
