package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataColumnSidecarCustodyImpl implements DataColumnSidecarCustody, SlotEventsChannel {

  interface BlockChainAccessor {

    Optional<Bytes32> getCanonicalBlockRootAtSlot(UInt64 slot);

  }

  private record SlotCustody(
      UInt64 slot,
      Optional<Bytes32> canonicalBlockRoot,
      List<UInt64> requiredColumnIndices,
      List<DataColumnIdentifier> custodiedColumnIndices
  ) {
    public Collection<DataColumnIdentifier> getIncompleteColumns() {
      return canonicalBlockRoot.map(blockRoot -> {
            Set<UInt64> collectedIndices = custodiedColumnIndices.stream()
                .filter(identifier -> identifier.getBlockRoot().equals(blockRoot))
                .map(DataColumnIdentifier::getIndex)
                .collect(Collectors.toSet());
            return requiredColumnIndices.stream()
                .filter(requiredColIdx -> !collectedIndices.contains(requiredColIdx))
                .map(missedColIdx -> new DataColumnIdentifier(blockRoot, missedColIdx));
          })
          .orElse(Stream.empty())
          .toList();
    }

    public boolean isIncomplete() {
      return !getIncompleteColumns().isEmpty();
    }
  }

  // for how long the custody will wait for a missing column to be gossiped
  private final UInt64 gossipWaitSlots = UInt64.ONE;

  private final Spec spec;
  private final DataColumnSidecarDB db;
  private final DataColumnSidecarRetriever retriever;
  private BlockChainAccessor blockChainAccessor;
  private UInt64 currentSlot; // TODO initialize and update it

  public DataColumnSidecarCustodyImpl(Spec spec, DataColumnSidecarDB db, DataColumnSidecarRetriever retriever) {
    this.spec = spec;
    this.db = db;
    this.retriever = retriever;
  }

  private UInt64 getEarliestCustodySlot(UInt64 currentSlot) {
    UInt64 epoch = getEarliestCustodyEpoch(spec.computeEpochAtSlot(currentSlot));
    return spec.computeStartSlotAtEpoch(epoch);
  }

  private UInt64 getEarliestCustodyEpoch(UInt64 currentEpoch) {
    int custodyPeriod = spec.getSpecConfig(currentEpoch).toVersionElectra().orElseThrow().getMinEpochsForDataColumnSidecarsRequests();
    return currentEpoch.minus(custodyPeriod);
  }

  private List<UInt64> getCustodyColumnsForSlot(UInt64 slot) {
    return getCustodyColumnsForEpoch(spec.computeEpochAtSlot(slot));
  }

  private List<UInt64> getCustodyColumnsForEpoch(UInt64 epoch) {
    // TODO
    throw new RuntimeException("TODO");
  }

  public void start() {

  }

  public void stop() {

  }

  @Override
  public void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    db.addSidecar(dataColumnSidecar);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(DataColumnIdentifier columnId) {
    return SafeFuture.completedFuture(db.getSidecar(columnId));
  }

  private void onEpoch(UInt64 epoch) {
    UInt64 pruneSlot = spec.computeStartSlotAtEpoch(getEarliestCustodyEpoch(epoch));
    db.pruneAllSidecars(pruneSlot);
  }

  @Override
  public void onSlot(UInt64 slot) {
    // TODO call onEpoch()
  }

  private void advanceLatestCompleteSlot() {
    streamSlotCustodies()
        .dropWhile(slotCustody -> !slotCustody.isIncomplete())
        .findFirst()
        .ifPresent(firstIncomplete -> db.setFirstIncompleteSlot(firstIncomplete.slot));
  }

  @SuppressWarnings("UnreachableCode") // TODO remove suppress later
  private Stream<SlotCustody> streamSlotCustodies() {
    UInt64 firstIncompleteSlot = db.getFirstIncompleteSlot()
        .orElseGet(() -> getEarliestCustodySlot(currentSlot));

    return Stream.iterate(firstIncompleteSlot, UInt64::increment)
        .map(slot -> {
          Optional<Bytes32> maybeCanonicalBlockRoot = blockChainAccessor.getCanonicalBlockRootAtSlot(slot);
          List<UInt64> requiredColumns = getCustodyColumnsForSlot(slot);
          List<DataColumnIdentifier> existingColumns = db.streamColumnIdentifiers(slot).toList();
          return new SlotCustody(slot, maybeCanonicalBlockRoot, requiredColumns, existingColumns);
        });
  }

  private Stream<ColumnSlotAndIdentifier> streamMissingColumns() {
    return streamSlotCustodies()
        .flatMap(slotCustody -> slotCustody.getIncompleteColumns()
            .stream().map(colId -> new ColumnSlotAndIdentifier(slotCustody.slot(), colId)));
  }

/*

  private class MissingColumnsTracker {

    private static class SlotData {
      final UInt64 slot;
      final Set<DataColumnIdentifier> receivedColumns = new HashSet<>();

      private SlotData(UInt64 slot) {
        this.slot = slot;
      }
    }

    private final NavigableMap<UInt64, SlotData> slotToData = new TreeMap<>();

    public MissingColumnsTracker(UInt64 earliestSlot, UInt64 latestSlot) {
      for (UInt64 slot = earliestSlot; earliestSlot.isLessThanOrEqualTo(latestSlot); slot = slot.increment()) {
        slotToData.put(slot, new SlotData(slot));
      }
    }

    public synchronized void columnAdded(UInt64 slot, DataColumnIdentifier columnIdentifier) {
      slotToData.computeIfAbsent(slot, SlotData::new).receivedColumns.add(columnIdentifier);
    }

    public synchronized void advanceSlot(UInt64 latestSlot) {
      slotToData.computeIfAbsent(latestSlot, SlotData::new);
    }

    private void pruneMap() {
      Optional<UInt64> latestCompleteSlot = getEarliestSlotWithMissingColumns().map(UInt64::decrement);
      Optional<UInt64> maybeFinalizedSlot = blockChainAccessor.getFinalizedSlot();
      maybeFinalizedSlot.ifPresent(finalizedSlot -> {
        UInt64 pruneSlot = finalizedSlot.min(latestCompleteSlot.orElse(finalizedSlot));
        slotToData.headMap(pruneSlot, true).clear();
      });
    }

    private Stream<ColumnSlotAndIdentifier> streamMissingColumnsAndSlot() {
      return slotToData.values().stream()
          .flatMap(slotToData -> {
            Optional<Bytes32> maybeCanonicalBlockRoot = blockChainAccessor.getCanonicalBlockRootAtSlot(slotToData.slot);
            return maybeCanonicalBlockRoot
                .map(canonicalBlockRoot -> {
                  HashSet<UInt64> requiredColumns = new HashSet<>(getCustodyColumnsForSlot(slotToData.slot));
                  for (DataColumnIdentifier receivedColumn : slotToData.receivedColumns) {
                    if (receivedColumn.getBlockRoot().equals(canonicalBlockRoot)) {
                      requiredColumns.remove(receivedColumn.getIndex());
                    }
                  }
                  return requiredColumns.stream()
                      .map(idx -> new ColumnSlotAndIdentifier(slotToData.slot, new DataColumnIdentifier(canonicalBlockRoot, idx)));
                })
                .orElse(Stream.empty());
          });
    }

    public synchronized Collection<DataColumnIdentifier> getMissingColumns() {
      return streamMissingColumnsAndSlot().map(ColumnSlotAndIdentifier::identifier)
          .toList();
    }

    public synchronized Optional<UInt64> getEarliestSlotWithMissingColumns() {
      return streamMissingColumnsAndSlot().map(ColumnSlotAndIdentifier::slot).min(Comparator.naturalOrder());
    }
  }
*/
}
