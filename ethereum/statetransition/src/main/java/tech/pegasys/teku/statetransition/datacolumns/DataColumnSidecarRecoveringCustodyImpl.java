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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;

public class DataColumnSidecarRecoveringCustodyImpl implements DataColumnSidecarRecoveringCustody {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataColumnSidecarByRootCustody delegate;
  private final AsyncRunner asyncRunner;
  private final MiscHelpersFulu miscHelpers;
  private final KZG kzg;
  private final SchemaDefinitionsFulu schemaDefinitions;
  private final Spec spec;

  private final long columnCount;
  private final int recoverColumnCount;
  private final boolean isSuperNode;

  final Function<UInt64, Duration> slotToRecoveryDelay;
  private final Map<SlotAndBlockRoot, RecoveryTask> recoveryTasks;

  private final Subscribers<DataColumnSidecarManager.ValidDataColumnSidecarsListener>
      validDataColumnSidecarsSubscribers = Subscribers.create(true);

  public DataColumnSidecarRecoveringCustodyImpl(
      final DataColumnSidecarByRootCustody delegate,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final MiscHelpersFulu miscHelpers,
      final KZG kzg,
      final SchemaDefinitionsFulu schemaDefinitions,
      final boolean isSuperNode,
      final int columnCount,
      final Function<UInt64, Duration> slotToRecoveryDelay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.miscHelpers = miscHelpers;
    this.kzg = kzg;
    this.schemaDefinitions = schemaDefinitions;
    this.spec = spec;
    this.recoveryTasks =
        LimitedMap.createSynchronizedNatural(spec.getGenesisSpec().getSlotsPerEpoch());
    this.isSuperNode = isSuperNode;
    this.slotToRecoveryDelay = slotToRecoveryDelay;
    this.columnCount = columnCount;
    this.recoverColumnCount = columnCount / 2;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (!isActiveSuperNode(slot)) {
      return;
    }
    // TODO: when and how to repeat this?
    asyncRunner
        .runAfterDelay(
            () -> {
              LOG.debug("Check missing identifiers for slot: {}", slot);

              final List<SlotAndBlockRoot> ids =
                  recoveryTasks.keySet().stream()
                      .filter(key -> key.getSlot().equals(slot))
                      .toList();
              ids.stream()
                  .map(recoveryTasks::get)
                  .forEach(
                      task -> {
                        AsyncStream<DataColumnSlotAndIdentifier>
                            dataColumnSlotAndIdentifierAsyncStream =
                                delegate.retrieveMissingColumns(task.block().getSlotAndBlockRoot());
                        final SafeFuture<List<DataColumnSlotAndIdentifier>> missingFuture =
                            dataColumnSlotAndIdentifierAsyncStream.toList();
                        missingFuture
                            .thenPeek(
                                missing -> {
                                  if (!missing.isEmpty()) {
                                    if (missing.size() <= recoverColumnCount) {
                                      LOG.info(
                                          " {} data column sidecars retrieved for {}. Starting full reconstruction",
                                          missing.size(),
                                          task.block.getSlotAndBlockRoot());
                                      prepareAndInitializeRecovery(task, missing);
                                    } else {
                                      LOG.info(
                                          "Only {} data column sidecars retrieved for {}. Cannot perform reconstruction",
                                          missing.size(),
                                          task.block.getSlotAndBlockRoot());
                                    }
                                  }
                                })
                            .ifExceptionGetsHereRaiseABug();
                      });
            },
            slotToRecoveryDelay.apply(slot))
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public void onNewBlock(
      final SignedBeaconBlock block, final Optional<BlobSidecarManager.RemoteOrigin> remoteOrigin) {
    if (!isActiveSuperNode(block.getSlot())) {
      return;
    }

    if (remoteOrigin.isPresent()
        && (remoteOrigin.get().equals(BlobSidecarManager.RemoteOrigin.LOCAL_EL)
            || remoteOrigin.get().equals(BlobSidecarManager.RemoteOrigin.LOCAL_PROPOSAL))) {
      // skip locally produced blocks, we will get everything for it in custody w/o reconstruction
      return;
    }
    recoveryTasks.put(block.getSlotAndBlockRoot(), new RecoveryTask(block));
  }

  @Override
  public void subscribeToValidDataColumnSidecars(
      final DataColumnSidecarManager.ValidDataColumnSidecarsListener sidecarsListener) {
    validDataColumnSidecarsSubscribers.subscribe(sidecarsListener);
  }

  private boolean isActiveSuperNode(final UInt64 slot) {
    return isSuperNode
        && spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU);
  }

  private record RecoveryTask(SignedBeaconBlock block) {}

  private void prepareAndInitializeRecovery(
      final RecoveryTask task, final List<DataColumnSlotAndIdentifier> missingIdentificators) {
    final Map<UInt64, DataColumnSlotAndIdentifier> missingMap =
        missingIdentificators.stream()
            .collect(
                Collectors.toMap(
                    DataColumnSlotAndIdentifier::columnIndex, identifier -> identifier));
    final SafeFuture<List<DataColumnSidecar>> list =
        AsyncStream.create(
                Stream.iterate(UInt64.ZERO, i -> i.isLessThan(columnCount), UInt64::increment)
                    .filter(key -> !missingMap.containsKey(key)))
            .mapAsync(
                i -> {
                  final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
                      new DataColumnSlotAndIdentifier(
                          task.block().getSlot(), task.block().getRoot(), i);
                  return delegate.getCustodyDataColumnSidecar(dataColumnSlotAndIdentifier);
                })
            .map(Optional::get)
            .toList();
    initiateRecovery(task.block(), list);
  }

  private void initiateRecovery(
      final SignedBeaconBlock block, final SafeFuture<List<DataColumnSidecar>> list) {
    LOG.info("Starting data columns sidecars recovery for block: {}", block.getSlotAndBlockRoot());

    list.thenPeek(
            sidecars -> {
              LOG.debug(
                  "Recovery for block: {}. DatacolumnSidecars found: {}",
                  block.getSlotAndBlockRoot(),
                  sidecars.size());
              final Map<UInt64, DataColumnSidecar> existingSidecarsByColIdx = new HashMap<>();
              sidecars.forEach(
                  sidecar -> existingSidecarsByColIdx.put(sidecar.getIndex(), sidecar));
              final List<DataColumnSidecar> recoveredSidecars =
                  miscHelpers.reconstructAllDataColumnSidecars(block.getMessage(), sidecars, kzg);
              recoveredSidecars.stream()
                  .filter(sidecar -> !existingSidecarsByColIdx.containsKey(sidecar.getIndex()))
                  .forEach(
                      dataColumnSidecar -> {
                        validDataColumnSidecarsSubscribers.forEach(
                            l -> l.onNewValidSidecar(dataColumnSidecar));
                        delegate
                            .onNewValidatedDataColumnSidecar(dataColumnSidecar)
                            .ifExceptionGetsHereRaiseABug();
                      });
              LOG.info(
                  "Data column sidecars recovery finished for block: {}",
                  block.getSlotAndBlockRoot());
            })
        .ifExceptionGetsHereRaiseABug();
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
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns(
      final SlotAndBlockRoot blockId) {
    return delegate.retrieveMissingColumns(blockId);
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
