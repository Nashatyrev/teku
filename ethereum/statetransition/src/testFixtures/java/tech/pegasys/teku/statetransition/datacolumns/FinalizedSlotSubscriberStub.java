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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

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
