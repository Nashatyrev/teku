package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;

/**
 * The underlying DB primary key for a sidecar is {@link ColumnSlotAndIdentifier}. When a sidecar is
 * requested by {@link DataColumnIdentifier} the slot for the block root needs to be queried This
 * class serves two purposes:
 *
 * <ul>
 *   <li>Optimizes extra call to the block DB when the slot is known from recent {@link
 *       #addSidecar(DataColumnSidecar)} call
 *   <li>Handles the case when a sidecar was stored optimistically without obtaining its
 *       corresponding block. In this case a subsequent sidecar query by {@link
 *       DataColumnIdentifier} would fail without this cache
 * </ul>
 */
class SlotIdCachingDasDb extends AbstractDelegatingDasDb implements DataColumnSidecarDB {
  private final DataColumnSidecarDB delegate;
  private final Map<DataColumnIdentifier, ColumnSlotAndIdentifier> cachedIdentifiers =
      new HashMap<>();
  private final NavigableMap<ColumnSlotAndIdentifier, DataColumnIdentifier> slotIdToIdentifiers =
      new TreeMap<>();

  public SlotIdCachingDasDb(DataColumnSidecarDB delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      DataColumnIdentifier dataColumnIdentifier) {
    final ColumnSlotAndIdentifier maybeCachedSlotId;
    synchronized (this) {
      maybeCachedSlotId = cachedIdentifiers.get(dataColumnIdentifier);
    }
    if (maybeCachedSlotId == null) {
      return super.getSidecar(dataColumnIdentifier);
    } else {
      return super.getSidecar(maybeCachedSlotId);
    }
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    final DataColumnIdentifier dataColumnIdentifier =
        DataColumnIdentifier.createFromSidecar(sidecar);
    synchronized (this) {
      ColumnSlotAndIdentifier columnSlotAndIdentifier =
          new ColumnSlotAndIdentifier(sidecar.getSlot(), dataColumnIdentifier);
      cachedIdentifiers.put(dataColumnIdentifier, columnSlotAndIdentifier);
      slotIdToIdentifiers.put(columnSlotAndIdentifier, dataColumnIdentifier);
    }
    super.addSidecar(sidecar);
  }

  private synchronized void pruneCaches(UInt64 tillSlot) {
    SortedMap<ColumnSlotAndIdentifier, DataColumnIdentifier> toPrune =
        slotIdToIdentifiers.headMap(ColumnSlotAndIdentifier.minimalForSlot(tillSlot));
    toPrune.values().forEach(cachedIdentifiers::remove);
    toPrune.clear();
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    pruneCaches(slot);
    return delegate.setFirstCustodyIncompleteSlot(slot);
  }

  // just delegate methods

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegate.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delegate.getFirstSamplerIncompleteSlot();
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delegate.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    delegate.pruneAllSidecars(tillSlot);
  }
}
