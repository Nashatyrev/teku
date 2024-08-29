package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockRootResolver;
import tech.pegasys.teku.statetransition.datacolumns.FinalizedSlotListener;

public interface MissingDasColumnsDb extends DataColumnSidecarDB {

  SafeFuture<Set<UInt64>> getMissingColumnIndexes(SlotAndBlockRoot blockId);

  void pruneCaches(UInt64 tillSlot);

  default DasColumnDbAccessor toDasColumnDbAccessor(
      CanonicalBlockRootResolver blockRootResolver,
      FinalizedSlotListener.Subscriber finalizedSlotSubscriber,
      UInt64 currentMinCustodySlot) {
    return new FirstIncompleteTrackingDasDb(
        this, blockRootResolver, finalizedSlotSubscriber, currentMinCustodySlot);
  }
}
