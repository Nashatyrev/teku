package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;

public interface CurrentSlotProvider {

  static CurrentSlotProvider create(Spec spec, ReadOnlyStore store) {
    return () -> spec.getCurrentSlot(store);
  }

  UInt64 getCurrentSlot();
}
