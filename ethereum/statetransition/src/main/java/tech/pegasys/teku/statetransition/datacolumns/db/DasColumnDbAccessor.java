package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Higher level {@link DataColumnSidecarDB} accessor
 */
public interface DasColumnDbAccessor {

  SafeFuture<UInt64> getOrCalculateFirstCustodyIncompleteSlot();

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier);

  SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot);

  SafeFuture<Set<UInt64>> getMissingColumnIndexes(SlotAndBlockRoot blockId);

  SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot();

  // update
  void addSidecar(DataColumnSidecar sidecar);

  SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot);

  default SafeFuture<Set<ColumnSlotAndIdentifier>> getMissingColumnIds(SlotAndBlockRoot blockId) {
    return getMissingColumnIndexes(blockId)
        .thenApply(
            colIndexes ->
                colIndexes.stream()
                    .map(
                        colIndex ->
                            new ColumnSlotAndIdentifier(
                                blockId.getSlot(),
                                new DataColumnIdentifier(blockId.getBlockRoot(), colIndex)))
                    .collect(Collectors.toSet()));
  }
}
