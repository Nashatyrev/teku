package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.BlockBlobChecker;
import tech.pegasys.teku.statetransition.datacolumns.CustodyFunction;

import java.util.BitSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface ColumnIdCachingDasDb extends DataColumnSidecarDB {

  Optional<BlockCache> getCached(SlotAndBlockRoot blockId);

  BlockCache getBlockCache(SlotAndBlockRoot blockId);

  default boolean hasAnyColumnsCached(SlotAndBlockRoot blockId) {
    return getCached(blockId).map(BlockCache::hasAnyCached).orElse(false);
  }

  void pruneCaches(UInt64 tillSlot);

  interface BlockCache {

    BitSet getCachedColumns();

    SafeFuture<BitSet> getColumnsBitmap();

    default SafeFuture<Set<UInt64>> getColumnIndexes() {
      return getColumnsBitmap()
          .thenApply(
              bitmap -> bitmap.stream().mapToObj(UInt64::valueOf).collect(Collectors.toSet()));
    }

    default boolean hasAnyCached() {
      return !getCachedColumns().isEmpty();
    }
  }

  default MissingDasColumnsDb withMissingColumnsTracking(
      BlockBlobChecker blockBlobChecker,
      CustodyFunction custodyFunction) {
    return new MissingDasColumnsDbImpl(this, blockBlobChecker, custodyFunction);
  }
}
