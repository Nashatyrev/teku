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

  private final NavigableMap<UInt64, Map<Bytes32, BlockColumnCache>> slotCaches = new TreeMap<>();

  public ColumnIdCachingDasDbImpl(DataColumnSidecarDB delegateDb, int numberOfColumns) {
    super(delegateDb);
    this.numberOfColumns = numberOfColumns;
  }

  private synchronized Map<Bytes32, BlockColumnCache> getSlotCaches(UInt64 slot) {
    return slotCaches.computeIfAbsent(slot, __ -> new HashMap<>());
  }

  @Override
  public synchronized BlockCache getBlockCache(SlotAndBlockRoot blockId) {
    return getBlockCacheImpl(blockId);
  }

  private BlockColumnCache getBlockCacheImpl(SlotAndBlockRoot blockId) {
    return getSlotCaches(blockId.getSlot())
        .computeIfAbsent(
            blockId.getBlockRoot(), __ -> new BlockColumnCache(() -> fetchBlockColumnIds(blockId)));
  }

  @Override
  public synchronized void pruneCaches(UInt64 tillSlot) {
    slotCaches.headMap(tillSlot).clear();
  }

  @Override
  public Optional<BlockCache> getCached(SlotAndBlockRoot blockId) {
    Map<Bytes32, BlockColumnCache> slotIds = slotCaches.get(blockId.getSlot());
    if (slotIds == null) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(slotIds.get(blockId.getBlockRoot()));
    }
  }

  private SafeFuture<Stream<DataColumnIdentifier>> fetchBlockColumnIds(SlotAndBlockRoot blockId) {
    return delegateDb
        .getColumnIdentifiers(blockId.getSlot())
        .thenApply(
            ids -> ids.stream().filter(id -> blockId.getBlockRoot().equals(id.getBlockRoot())));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return SafeFuture.collectAll(
            getSlotCaches(slot).entrySet().stream()
                .map(entry -> entry.getValue().generateColumnIds(entry.getKey())))
        .thenApply(listOfIds -> listOfIds.stream().flatMap(Collection::stream).toList());
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    super.addSidecar(sidecar);
    getBlockCacheImpl(new SlotAndBlockRoot(sidecar.getSlot(), sidecar.getBlockRoot()))
        .onColumn(sidecar.getIndex());
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    super.pruneAllSidecars(tillSlot);
    pruneCaches(tillSlot);
  }

  private class BlockColumnCache implements BlockCache {
    private final BitSet possessedColumns = new BitSet(numberOfColumns);
    private final Supplier<SafeFuture<Void>> lazyDbMergeTask;

    private BlockColumnCache(Supplier<SafeFuture<Stream<DataColumnIdentifier>>> lazyDBFetcher) {
      lazyDbMergeTask = Suppliers.memoize(() -> merge(lazyDBFetcher.get()));
    }

    public synchronized void onColumn(UInt64 columnIndex) {
      possessedColumns.set(columnIndex.intValue());
    }

    public SafeFuture<List<DataColumnIdentifier>> generateColumnIds(Bytes32 blockRoot) {
      return lazyDbMergeTask.get().thenApply(__ -> generateIds(blockRoot));
    }

    private SafeFuture<Void> merge(SafeFuture<Stream<DataColumnIdentifier>> blockColumnsPromise) {
      return blockColumnsPromise.thenAccept(this::merge);
    }

    private synchronized void merge(Stream<DataColumnIdentifier> identifiers) {
      identifiers.forEach(id -> onColumn(id.getIndex()));
    }

    private synchronized List<DataColumnIdentifier> generateIds(Bytes32 blockRoot) {
      return possessedColumns.stream()
          .mapToObj(colIdx -> new DataColumnIdentifier(blockRoot, UInt64.valueOf(colIdx)))
          .toList();
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
