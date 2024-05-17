package tech.pegasys.teku.statetransition.datacolumns.retriever;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellID;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDB;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RecoveringSidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataColumnSidecarRetriever delegate;
  private final KZG kzg;
  private final MiscHelpersEip7594 specHelpers;
  private final CanonicalBlockResolver blockResolver;
  private final DataColumnSidecarDB sidecarDB;
  private final AsyncRunner asyncRunner;
  private final Duration recoverInitiationTimeout;

  private final Map<UInt64, RecoveryEntry> recoveryBySlot = new ConcurrentHashMap<>();

  public RecoveringSidecarRetriever(
      DataColumnSidecarRetriever delegate,
      KZG kzg,
      MiscHelpersEip7594 specHelpers,
      CanonicalBlockResolver blockResolver,
      DataColumnSidecarDB sidecarDB,
      AsyncRunner asyncRunner,
      Duration recoverInitiationTimeout) {
    this.delegate = delegate;
    this.kzg = kzg;
    this.specHelpers = specHelpers;
    this.blockResolver = blockResolver;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoverInitiationTimeout = recoverInitiationTimeout;
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(ColumnSlotAndIdentifier columnId) {
    SafeFuture<DataColumnSidecar> promise = delegate.retrieve(columnId);
    asyncRunner.runAfterDelay(
        () -> {
          if (!promise.isDone()) {
            maybeInitiateRecovery(columnId, promise);
          }
        },
        recoverInitiationTimeout);
    return promise;
  }

  @VisibleForTesting
  void maybeInitiateRecovery(
      ColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> promise) {
    Optional<BeaconBlock> maybeBlock = blockResolver.getBlockAtSlot(columnId.slot());
    if (!maybeBlock
        .map(b -> b.getRoot().equals(columnId.identifier().getBlockRoot()))
        .orElse(false)) {
      promise.completeExceptionally(new NotOnCanonicalChainException(columnId, maybeBlock));
    } else {
      BeaconBlock block = maybeBlock.orElseThrow();
      RecoveryEntry recovery =
          recoveryBySlot.compute(
              columnId.slot(),
              (slot, existingRecovery) -> {
                if (existingRecovery != null
                    && !existingRecovery.block.getRoot().equals(block.getRoot())) {
                  // we are recovering obsolete column which is no more on our canonical chain
                  existingRecovery.cancel();
                }
                if (existingRecovery == null || existingRecovery.cancelled) {
                  return createNewRecovery(block);
                } else {
                  return existingRecovery;
                }
              });
      LOG.info("[nyota] Recovery: initiated recovery for " + columnId);
      recovery.addRequest(columnId.identifier().getIndex(), promise);
    }
  }

  private void recoveryComplete(RecoveryEntry entry) {
    recoveryBySlot.remove(entry.block.getSlot(), entry);
  }

  private RecoveryEntry createNewRecovery(BeaconBlock block) {
    RecoveryEntry recoveryEntry = new RecoveryEntry(block, kzg, specHelpers);
    sidecarDB
        .streamColumnIdentifiers(block.getSlot())
        .forEach(
            colId -> {
              Optional<DataColumnSidecar> maybeSidecar = sidecarDB.getSidecar(colId);
              maybeSidecar.ifPresent(recoveryEntry::addSidecar);
            });
    recoveryEntry.initRecoveryRequests();
    return recoveryEntry;
  }

  private class RecoveryEntry {
    private final BeaconBlock block;
    private final KZG kzg;
    private final MiscHelpersEip7594 specHelpers;
    private final int columnCount = 128;

    private final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx = new HashMap<>();
    private final Map<UInt64, List<CompletableFuture<DataColumnSidecar>>> promisesByColIdx =
        new HashMap<>();
    private List<SafeFuture<DataColumnSidecar>> recoveryRequests;
    private final int recoverColumnCount = columnCount / 2;
    private boolean recovered = false;
    private boolean cancelled = false;

    public RecoveryEntry(BeaconBlock block, KZG kzg, MiscHelpersEip7594 specHelpers) {
      this.block = block;
      this.kzg = kzg;
      this.specHelpers = specHelpers;
    }

    public synchronized void addRequest(
        UInt64 columnIndex, CompletableFuture<DataColumnSidecar> promise) {
      if (recovered) {
        return;
      }
      promisesByColIdx.computeIfAbsent(columnIndex, __ -> new ArrayList<>()).add(promise);
    }

    public synchronized void addSidecar(DataColumnSidecar sidecar) {
      if (!recovered && sidecar.getBlockRoot().equals(block.getRoot())) {
        existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar);
        if (existingSidecarsByColIdx.size() >= recoverColumnCount) {
          // TODO: Make it asynchronously as it's heavy CPU operation
          recover();
          recoveryComplete();
        }
      }
    }

    private void recoveryComplete() {
      recovered = true;
      LOG.info(
          "[nyota] Recovery: completed for the slot {}, requests complete: {}",
          block.getSlot(),
          promisesByColIdx.values().stream().mapToInt(List::size).sum());

      promisesByColIdx.forEach(
          (key, value) -> {
            DataColumnSidecar columnSidecar = existingSidecarsByColIdx.get(key);
            value.forEach(promise -> promise.complete(columnSidecar));
          });
      promisesByColIdx.clear();
      RecoveringSidecarRetriever.this.recoveryComplete(this);
      if (recoveryRequests != null) {
        recoveryRequests.forEach(r -> r.cancel(true));
        recoveryRequests = null;
      }
    }

    public synchronized void initRecoveryRequests() {
      if (!recovered && !cancelled) {
        recoveryRequests =
            IntStream.range(0, columnCount)
                .mapToObj(UInt64::valueOf)
                .filter(idx -> !existingSidecarsByColIdx.containsKey(idx))
                .map(
                    columnIdx ->
                        delegate.retrieve(
                            new ColumnSlotAndIdentifier(
                                block.getSlot(),
                                new DataColumnIdentifier(block.getRoot(), columnIdx))))
                .peek(promise -> promise.thenPeek(this::addSidecar))
                .toList();
      }
    }

    public synchronized void cancel() {
      cancelled = true;
      promisesByColIdx.values().stream()
          .flatMap(Collection::stream)
          .forEach(
              promise ->
                  promise.completeExceptionally(
                      new NotOnCanonicalChainException("Canonical block changed")));
    }

    private void recover() {
      List<List<KZGCellWithColumnId>> columnBlobCells =
          existingSidecarsByColIdx.values().stream()
              .map(
                  sideCar ->
                      sideCar.getDataColumn().stream()
                          .map(
                              cell ->
                                  new KZGCellWithColumnId(
                                      new KZGCell(cell.getBytes()),
                                      new KZGCellID(sideCar.getIndex())))
                          .toList())
              .toList();
      List<List<KZGCellWithColumnId>> blobColumnCells = transpose(columnBlobCells);
      List<Bytes> recoveredBlobs =
          blobColumnCells.stream().parallel().map(kzg::recoverCells).map(kzg::computeBlob).toList();
      DataColumnSidecar anyExistingSidecar =
          existingSidecarsByColIdx.values().stream().findFirst().orElseThrow();
      SignedBeaconBlockHeader signedBeaconBlockHeader =
          anyExistingSidecar.getSignedBeaconBlockHeader();
      List<DataColumnSidecar> recoveredSidecars =
          specHelpers.constructDataColumnSidecars(
              block, signedBeaconBlockHeader, recoveredBlobs, kzg);
      Map<UInt64, DataColumnSidecar> recoveredSidecarsAsMap =
          recoveredSidecars.stream()
              .collect(Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, i -> i));
      existingSidecarsByColIdx.putAll(recoveredSidecarsAsMap);
    }
  }

  private static <T> List<List<T>> transpose(List<List<T>> matrix) {
    int rowCount = matrix.size();
    int colCount = matrix.get(0).size();
    List<List<T>> ret =
        Stream.generate(() -> (List<T>) new ArrayList<T>(rowCount)).limit(colCount).toList();

    for (int row = 0; row < rowCount; row++) {
      if (matrix.get(row).size() != colCount) {
        throw new IllegalArgumentException("Different number columns in the matrix");
      }
      for (int col = 0; col < colCount; col++) {
        T val = matrix.get(row).get(col);
        ret.get(col).add(row, val);
      }
    }
    return ret;
  }
}
