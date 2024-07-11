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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodyEip7594;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DataColumnSidecarSamplerImpl
    implements DataColumnSidecarSampler, SlotEventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger("das-nyota");
  private static final int MAX_SCAN_SLOTS = 200;

  private final Spec spec;
  private final DataColumnSidecarDB db;
  private final CanonicalBlockResolver blockResolver;
  private final int myDataColumnSampleCount;

  private final UInt64 eip7594StartEpoch;
  private final Random rnd;
  private final TreeMap<UInt64, List<UInt64>> assignedSampleColumns = new TreeMap<>();
  private final TreeMap<UInt64, Set<DataColumnIdentifier>> collectedSamples = new TreeMap<>();

  private UInt64 currentSlot = null;

  public DataColumnSidecarSamplerImpl(
      Spec spec,
      CanonicalBlockResolver blockResolver,
      DataColumnSidecarDB db,
      int myDataColumnSampleCount) {

    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(db);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.myDataColumnSampleCount = myDataColumnSampleCount;
    this.eip7594StartEpoch = spec.getForkSchedule().getFork(SpecMilestone.EIP7594).getEpoch();
    this.rnd = new Random();
  }

  private UInt64 getEarliestSampleSlot(UInt64 currentSlot) {
    UInt64 epoch = getEarliestSamplesEpoch(spec.computeEpochAtSlot(currentSlot));
    return spec.computeStartSlotAtEpoch(epoch);
  }

  private UInt64 getEarliestSamplesEpoch(UInt64 currentEpoch) {
    int custodyPeriod =
        spec.getSpecConfig(currentEpoch)
            .toVersionEip7594()
            .orElseThrow()
            .getMinEpochsForDataColumnSidecarsRequests();
    return currentEpoch.minusMinZero(custodyPeriod).max(eip7594StartEpoch);
  }

  @Override
  public synchronized void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    getFirstIncompleteSlot()
        .thenAccept(
            firstIncompleteSlot -> {
              if (dataColumnSidecar.getSlot().isGreaterThanOrEqualTo(firstIncompleteSlot)) {
                final Set<DataColumnIdentifier> newIdentifiers =
                    onMaybeNewValidatedIdentifier(
                        dataColumnSidecar.getSlot(),
                        DataColumnIdentifier.createFromSidecar(dataColumnSidecar));

                // IF we've collected 50%, everything from this slot is available
                final Set<DataColumnIdentifier> thisSlotDataColumnIdentifiers =
                    collectedSamples.get(dataColumnSidecar.getSlot());
                final int columnsCount =
                    SpecConfigEip7594.required(spec.atSlot(dataColumnSidecar.getSlot()).getConfig())
                        .getNumberOfColumns();
                if (thisSlotDataColumnIdentifiers.size() * 2 >= columnsCount
                    && thisSlotDataColumnIdentifiers.size() != columnsCount) {
                  IntStream.range(0, columnsCount)
                      .mapToObj(
                          index ->
                              new DataColumnIdentifier(
                                  dataColumnSidecar.getBlockRoot(), UInt64.valueOf(index)))
                      .forEach(
                          identifier -> {
                            final boolean wasAdded = thisSlotDataColumnIdentifiers.add(identifier);
                            if (wasAdded) {
                              newIdentifiers.add(identifier);
                            }
                          });
                }

                // Debug logging
                if (areSlotAssignedColumnsCompleted(dataColumnSidecar.getSlot())
                    // Were it just completed, on this DataColumnSidecar?
                    && assignedSampleColumns.get(dataColumnSidecar.getSlot()).stream()
                        .map(
                            assignedIndex ->
                                newIdentifiers.contains(
                                    new DataColumnIdentifier(
                                        dataColumnSidecar.getBlockRoot(), assignedIndex)))
                        .filter(couldBeTrue -> couldBeTrue)
                        .findFirst()
                        .orElse(false)) {
                  LOG.info(
                      "Slot {} sampling is completed successfully", dataColumnSidecar.getSlot());
                }
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private Set<DataColumnIdentifier> onMaybeNewValidatedIdentifier(
      final UInt64 slot, final DataColumnIdentifier dataColumnIdentifier) {
    final Set<DataColumnIdentifier> newIdentifiers = new HashSet<>();
    collectedSamples.compute(
        slot,
        (__, dataColumnIdentifiers) -> {
          if (dataColumnIdentifiers != null) {
            final boolean wasAdded = dataColumnIdentifiers.add(dataColumnIdentifier);
            if (wasAdded) {
              newIdentifiers.add(dataColumnIdentifier);
            }
            return dataColumnIdentifiers;
          } else {
            newIdentifiers.add(dataColumnIdentifier);
            Set<DataColumnIdentifier> collectedIdentifiers = new HashSet<>();
            collectedIdentifiers.add(dataColumnIdentifier);
            return collectedIdentifiers;
          }
        });

    return newIdentifiers;
  }

  private boolean areSlotAssignedColumnsCompleted(final UInt64 slot) {
    return assignedSampleColumns.containsKey(slot)
        // All assigned are collected
        && assignedSampleColumns.get(slot).stream()
            .map(
                assignedIndex ->
                    collectedSamples.get(slot).stream()
                        .map(DataColumnIdentifier::getIndex)
                        .filter(collectedIndex -> collectedIndex.equals(assignedIndex))
                        .map(__ -> true)
                        .findFirst()
                        .orElse(false))
            .filter(columnResult -> columnResult.equals(false))
            .findFirst()
            .orElse(true);
  }

  private synchronized List<UInt64> getSampleColumns(UInt64 slot) {
    final boolean alreadyAssigned = assignedSampleColumns.containsKey(slot);
    final List<UInt64> assignedSampleColumns =
        this.assignedSampleColumns.computeIfAbsent(slot, this::computeSampleColumns);
    if (!alreadyAssigned) {
      LOG.info("Slot {} assigned columns: {}", slot, assignedSampleColumns);
    }

    return assignedSampleColumns;
  }

  private List<UInt64> computeSampleColumns(UInt64 slot) {
    final int columnsCount =
        SpecConfigEip7594.required(spec.atSlot(slot).getConfig()).getNumberOfColumns();
    final List<UInt64> assignedSamples = new ArrayList<>();
    while (assignedSamples.size() < myDataColumnSampleCount) {
      final UInt64 candidate = UInt64.valueOf(rnd.nextInt(columnsCount));
      if (assignedSamples.contains(candidate)) {
        continue;
      }
      assignedSamples.add(candidate);
    }

    return assignedSamples;
  }

  @Override
  public void onSlot(UInt64 slot) {
    currentSlot = slot;
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch()).ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<Void> advanceFirstIncompleteSlot(UInt64 finalizedEpoch) {
    UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());

    return retrievePotentiallyIncompleteSlotSamples(firstNonFinalizedSlot, MAX_SCAN_SLOTS)
        .thenAccept(
            slotTasks ->
                slotTasks.stream()
                    .filter(SlotColumnsTask::isIncomplete)
                    .findFirst()
                    .ifPresentOrElse(
                        slotColumnsTask ->
                            db.setFirstSamplerIncompleteSlot(slotColumnsTask.slot())
                                .thenPeek(__ -> prune(slotColumnsTask.slot()))
                                .ifExceptionGetsHereRaiseABug(),
                        () -> {
                          if (slotTasks.isEmpty()) {
                            return;
                          }
                          db.setFirstSamplerIncompleteSlot(
                                  slotTasks.get(slotTasks.size() - 1).slot())
                              .thenPeek(__ -> prune(slotTasks.get(slotTasks.size() - 1).slot()))
                              .ifExceptionGetsHereRaiseABug();
                        }));
  }

  private synchronized void prune(final UInt64 slotExclusive) {
    LOG.info(
        "Pruning till slot {}, collectedSamples: {}, assignedSamples: {}",
        slotExclusive,
        collectedSamples.subMap(UInt64.ZERO, slotExclusive).keySet().stream().sorted().toList(),
        assignedSampleColumns.subMap(UInt64.ZERO, slotExclusive).keySet().stream()
            .sorted()
            .toList());
    final Set<UInt64> collectedSampleSlotsToPrune =
        collectedSamples.subMap(UInt64.ZERO, slotExclusive).keySet();
    collectedSampleSlotsToPrune.forEach(collectedSamples::remove);
    final Set<UInt64> assignedSamplesSlotsToPrune =
        assignedSampleColumns.subMap(UInt64.ZERO, slotExclusive).keySet();
    assignedSamplesSlotsToPrune.forEach(assignedSampleColumns::remove);
  }

  private SafeFuture<List<SlotColumnsTask>> retrievePotentiallyIncompleteSlotSamples(
      final UInt64 toSlotIncluded, final int limit) {
    return getFirstIncompleteSlot()
        .thenCompose(
            firstIncompleteSlot -> {
              final UInt64 toSlot;
              if (firstIncompleteSlot.plus(limit).isLessThan(toSlotIncluded)) {
                toSlot = firstIncompleteSlot.plus(limit);
              } else {
                toSlot = toSlotIncluded;
              }
              Stream<SafeFuture<SlotColumnsTask>> slotSamplesStream =
                  Stream.iterate(
                          firstIncompleteSlot,
                          slot -> slot.isLessThanOrEqualTo(toSlot),
                          UInt64::increment)
                      .map(this::createSlotColumnsTask);
              return SafeFuture.collectAll(slotSamplesStream);
            });
  }

  private SafeFuture<SlotColumnsTask> createSlotColumnsTask(final UInt64 slot) {
    final SafeFuture<Optional<Bytes32>> maybeCanonicalBlockRootFuture =
        getBlockRootIfHaveBlobs(slot);
    final List<UInt64> requiredColumns = getSampleColumns(slot);
    return maybeCanonicalBlockRootFuture.thenApply(
        maybeCanonicalBlockRoot ->
            new SlotColumnsTask(
                slot,
                maybeCanonicalBlockRoot,
                requiredColumns,
                collectedSamples.getOrDefault(slot, Collections.emptySet())));
  }

  //    if (currentSlot == null) {
  //      return Stream.empty();
  //    }

  private SafeFuture<UInt64> getFirstIncompleteSlot() {
    return db.getFirstSamplerIncompleteSlot()
        .thenApply(maybeSlot -> maybeSlot.orElseGet(() -> getEarliestSampleSlot(currentSlot)));
  }

  private SafeFuture<Optional<Bytes32>> getBlockRootIfHaveBlobs(UInt64 slot) {
    return blockResolver
        .getBlockAtSlot(slot)
        .thenApply(
            maybeBlock -> {
              final Optional<BeaconBlockBodyEip7594> beaconBlockBodyEip7594 =
                  maybeBlock.flatMap(block -> block.getBody().toVersionEip7594());
              final int kzzCommitmentsCount =
                  beaconBlockBodyEip7594.map(body -> body.getBlobKzgCommitments().size()).orElse(0);
              if (kzzCommitmentsCount > 0) {
                return Optional.of(maybeBlock.get().getRoot());
              } else {
                return Optional.empty();
              }
            });
  }

  @Override
  public SafeFuture<List<ColumnSlotAndIdentifier>> retrieveMissingColumns() {
    // waiting a column for [gossipWaitSlots] to be delivered by gossip
    // and not considering it missing yet
    return retrievePotentiallyIncompleteSlotSamples(currentSlot, MAX_SCAN_SLOTS)
        .thenApply(
            slotTasks ->
                slotTasks.stream()
                    .flatMap(
                        slotTask ->
                            slotTask.getIncompleteColumns().stream()
                                .map(colId -> new ColumnSlotAndIdentifier(slotTask.slot(), colId)))
                    .toList());
  }
}
