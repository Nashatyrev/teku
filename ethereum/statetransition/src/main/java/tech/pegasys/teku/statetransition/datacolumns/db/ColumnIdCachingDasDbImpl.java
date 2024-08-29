package tech.pegasys.teku.statetransition.datacolumns.db;

import com.google.common.base.Suppliers;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

class ColumnIdCachingDasDbImpl extends AbstractDelegatingDasDb
    implements ColumnIdCachingDasDb {

  private final int numberOfColumns;

  private final NavigableMap<UInt64, SlotCache> slotCaches = new TreeMap<>();

  public ColumnIdCachingDasDbImpl(DataColumnSidecarDB delegateDb, int numberOfColumns) {
    super(delegateDb);
    this.numberOfColumns = numberOfColumns;
  }

  private synchronized SlotCache getOrCreateSlotCache(UInt64 slot) {
    return slotCaches.computeIfAbsent(slot, __ -> new SlotCache(() -> delegateDb.getColumnIdentifiers(slot)));
  }

  @Override
  public BlockCache getBlockCache(SlotAndBlockRoot blockId) {
    return getOrCreateBlockCache(blockId);
  }

  private BlockCacheImpl getOrCreateBlockCache(SlotAndBlockRoot blockId) {
    return getOrCreateSlotCache(blockId.getSlot()).getOrCreateBlockCache(blockId.getBlockRoot());
  }

  @Override
  public Optional<BlockCache> getCached(SlotAndBlockRoot blockId) {
    return Optional.ofNullable(slotCaches.get(blockId.getSlot())).flatMap(slotCache -> slotCache.getCached(blockId.getBlockRoot()));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return getOrCreateSlotCache(slot).getAllColumnIdentifiers();
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    super.addSidecar(sidecar);
    getOrCreateBlockCache(new SlotAndBlockRoot(sidecar.getSlot(), sidecar.getBlockRoot()))
        .onColumn(sidecar.getIndex());
  }

  @Override
  public synchronized void pruneCaches(UInt64 tillSlot) {
    slotCaches.headMap(tillSlot).clear();
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    super.pruneAllSidecars(tillSlot);
    pruneCaches(tillSlot);
  }

  private class SlotCache {
    private final Map<Bytes32, BlockCacheImpl> blocksCache = new HashMap<>();
    private final Supplier<SafeFuture<Void>> lazyDbMergeTask;

    public synchronized BlockCacheImpl getOrCreateBlockCache(Bytes32 blockRoot) {
      return blocksCache.computeIfAbsent(blockRoot, __ -> new BlockCacheImpl(lazyDbMergeTask));
    }

    private SlotCache(Supplier<SafeFuture<List<DataColumnIdentifier>>> lazyDBFetcher) {
      lazyDbMergeTask = Suppliers.memoize(() -> merge(lazyDBFetcher.get()));
    }

    private SafeFuture<Void> merge(SafeFuture<List<DataColumnIdentifier>> blockColumnsPromise) {
      return blockColumnsPromise.thenAccept(this::merge);
    }

    private void merge(List<DataColumnIdentifier> identifiers) {
      identifiers.forEach(this::onColumn);
    }

    public void onColumn(DataColumnIdentifier columnId) {
      BlockCacheImpl blockCache = getOrCreateBlockCache(columnId.getBlockRoot());
      blockCache.onColumn(columnId.getIndex());
    }

    public Optional<BlockCacheImpl> getCached(Bytes32 blockRoot) {
      return Optional.ofNullable(blocksCache.get(blockRoot));
    }

    SafeFuture<List<DataColumnIdentifier>> getAllColumnIdentifiers() {
      return lazyDbMergeTask
          .get()
          .thenApply(
              __ ->
                  blocksCache.entrySet().stream()
                      .flatMap(entry -> entry.getValue().streamColumnIds(entry.getKey()))
                      .toList());
    }
  }

  private class BlockCacheImpl implements BlockCache {
    private final BitSet possessedColumns = new BitSet(numberOfColumns);
    private final Supplier<SafeFuture<Void>> lazyDbMergeTask;

    public BlockCacheImpl(Supplier<SafeFuture<Void>> lazyDbMergeTask) {
      this.lazyDbMergeTask = lazyDbMergeTask;
    }

    public synchronized void onColumn(UInt64 columnIndex) {
      possessedColumns.set(columnIndex.intValue());
    }

    public synchronized Stream<DataColumnIdentifier> streamColumnIds(Bytes32 blockRoot) {
      return possessedColumns.stream()
          .mapToObj(colIdx -> new DataColumnIdentifier(blockRoot, UInt64.valueOf(colIdx)));
    }

    @Override
    public BitSet getCachedColumns() {
      return possessedColumns;
    }

    @Override
    public SafeFuture<BitSet> getColumnsBitmap() {
      return lazyDbMergeTask.get().thenApply(__ -> possessedColumns);
    }
  }
}
