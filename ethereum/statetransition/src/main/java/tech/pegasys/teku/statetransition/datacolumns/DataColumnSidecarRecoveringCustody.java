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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class DataColumnSidecarRecoveringCustody
    implements DataColumnSidecarByRootCustody,
        UpdatableDataColumnSidecarCustody,
        SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  // TODO: I don't like such type
  private final DataColumnSidecarByRootCustodyImpl delegate;
  private final AsyncRunner asyncRunner;
  private final MiscHelpersFulu miscHelpers;
  private final KZG kzg;
  private final SchemaDefinitionsFulu schemaDefinitions;
  private final CanonicalBlockResolver blockResolver;

  private final long columnCount;
  private final int recoverColumnCount;

  private final Duration inSlotRecoveryDelay;

  public DataColumnSidecarRecoveringCustody(
      final DataColumnSidecarByRootCustodyImpl delegate,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final MiscHelpersFulu miscHelpers,
      final KZG kzg,
      final SchemaDefinitionsFulu schemaDefinitions,
      final CanonicalBlockResolver blockResolver,
      final int columnCount) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.miscHelpers = miscHelpers;
    this.kzg = kzg;
    this.schemaDefinitions = schemaDefinitions;
    this.blockResolver = blockResolver;
    // TODO: pass function depending on slot # instead in constructor
    this.inSlotRecoveryDelay =
        Duration.ofMillis(spec.getMillisPerSlot(UInt64.ZERO).dividedBy(3).longValue());
    this.columnCount = columnCount;
    this.recoverColumnCount = columnCount / 2;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // TODO: when and how to repeat this?
    asyncRunner
        .runAfterDelay(
            () -> {
              LOG.info("Check missing identifiers for slot: {}", slot);
              final SafeFuture<Optional<DataColumnSlotAndIdentifier>> maybeMissingFuture =
                  delegate
                      .retrieveMissingColumns()
                      .filter(id -> id.slot().equals(slot))
                      .findFirst();
              maybeMissingFuture
                  .thenCompose(
                      maybeMissing -> {
                        if (maybeMissing.isEmpty()) {
                          // TODO: I don't like EMPTY
                          return SafeFuture.completedFuture(Pair.of(Bytes32.EMPTY, columnCount));
                        } else {
                          final DataColumnSlotAndIdentifier missing = maybeMissing.get();
                          return AsyncStream.create(
                                  Stream.iterate(
                                          UInt64.ZERO,
                                          i -> i.isLessThanOrEqualTo(columnCount),
                                          UInt64::increment)
                                      .map(
                                          i ->
                                              delegate.hasCustodyDataColumnSidecar(
                                                  new DataColumnSlotAndIdentifier(
                                                      missing.slot(), missing.blockRoot(), i))))
                              .collect(Collectors.counting())
                              .thenApply(count -> Pair.of(missing.blockRoot(), count));
                        }
                      })
                  .thenPeek(
                      pair -> {
                        switch (pair.getRight()) {
                          case Long __ when pair.getRight() == columnCount ->
                              LOG.info("All done for slot: {}", slot);
                          case Long col when pair.getRight() < recoverColumnCount ->
                              LOG.info(
                                  "Not enough columns ({}) to recover for slot: {}", col, slot);
                          default -> initiateRecovery((Bytes32) pair.getLeft(), slot);
                        }
                      })
                  .ifExceptionGetsHereRaiseABug();
            },
            inSlotRecoveryDelay)
        .ifExceptionGetsHereRaiseABug();
  }

  // TODO what if we have 2 blockRoots case
  private void initiateRecovery(final Bytes32 blockRoot, final UInt64 slot) {
    LOG.info("Starting recovery for slot: {}, blockRoot: {}", slot, blockRoot);

    final SafeFuture<List<DataColumnSidecar>> list =
        AsyncStream.create(
                Stream.iterate(
                    UInt64.ZERO, i -> i.isLessThanOrEqualTo(columnCount), UInt64::increment))
            .mapAsync(
                i ->
                    delegate.getCustodyDataColumnSidecar(
                        new DataColumnSlotAndIdentifier(slot, blockRoot, i)))
            .map(Optional::get)
            .toList();
    list.thenPeek(
            sidecars -> {
              final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx = new HashMap<>();
              sidecars.forEach(
                  sidecar -> existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar));
              LOG.info(
                  "Recovery for slot: {}, blockRoot: {}. DatacolumnSidecars found: {}",
                  slot,
                  blockRoot,
                  sidecars.size());
              // TODO: reuse, not copy
              final List<List<MatrixEntry>> columnBlobEntries =
                  existingSidecarsByColIdx.values().stream()
                      .map(
                          sideCar ->
                              IntStream.range(0, sideCar.getDataColumn().size())
                                  .mapToObj(
                                      rowIndex ->
                                          schemaDefinitions
                                              .getMatrixEntrySchema()
                                              .create(
                                                  sideCar.getDataColumn().get(rowIndex),
                                                  sideCar
                                                      .getSszKZGProofs()
                                                      .get(rowIndex)
                                                      .getKZGProof(),
                                                  sideCar.getIndex(),
                                                  UInt64.valueOf(rowIndex)))
                                  .toList())
                      .toList();
              final List<List<MatrixEntry>> blobColumnEntries = transpose(columnBlobEntries);
              final List<List<MatrixEntry>> extendedMatrix =
                  miscHelpers.recoverMatrix(blobColumnEntries, kzg);
              final DataColumnSidecar anyExistingSidecar =
                  existingSidecarsByColIdx.values().stream().findFirst().orElseThrow();
              final SignedBeaconBlockHeader signedBeaconBlockHeader =
                  anyExistingSidecar.getSignedBeaconBlockHeader();
              // TODO: refactor
              blockResolver
                  .getBlockAtSlot(slot)
                  .thenPeek(
                      maybeBlock -> {
                        if (maybeBlock.isEmpty()) {
                          LOG.error("Unexpected: Block for slot {} is empty", slot);
                        }
                        final List<DataColumnSidecar> recoveredSidecars =
                            miscHelpers.constructDataColumnSidecars(
                                maybeBlock.orElseThrow(), signedBeaconBlockHeader, extendedMatrix);
                        recoveredSidecars.stream()
                            .filter(
                                sidecar ->
                                    !existingSidecarsByColIdx.containsKey(sidecar.getIndex()))
                            .forEach(
                                dataColumnSidecar ->
                                    delegate
                                        .onNewValidatedDataColumnSidecar(dataColumnSidecar)
                                        .ifExceptionGetsHereRaiseABug());
                        LOG.info("Recovery finished for slot: {}, blockRoot: {}", slot, blockRoot);
                      })
                  .ifExceptionGetsHereRaiseABug();
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private static <T> List<List<T>> transpose(final List<List<T>> matrix) {
    final int rowCount = matrix.size();
    final int colCount = matrix.get(0).size();
    final List<List<T>> ret =
        Stream.generate(() -> (List<T>) new ArrayList<T>(rowCount)).limit(colCount).toList();

    for (int row = 0; row < rowCount; row++) {
      if (matrix.get(row).size() != colCount) {
        throw new IllegalArgumentException("Different number columns in the matrix");
      }
      for (int col = 0; col < colCount; col++) {
        final T val = matrix.get(row).get(col);
        ret.get(col).add(row, val);
      }
    }
    return ret;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      final DataColumnIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecarByRoot(columnId);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    return delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.hasCustodyDataColumnSidecar(columnId);
  }
}
