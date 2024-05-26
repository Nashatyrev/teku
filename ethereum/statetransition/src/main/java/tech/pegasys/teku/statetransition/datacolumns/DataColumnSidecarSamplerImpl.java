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
import static tech.pegasys.teku.statetransition.datacolumns.CompleteIncompleteSlot.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DataColumnSidecarSamplerImpl
    implements DataColumnSidecarSampler, SlotEventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final Spec spec;
  private final DataColumnSidecarDB db;
  private final CanonicalBlockResolver blockResolver;
  private final UInt256 nodeId;
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
      UInt256 nodeId,
      int myDataColumnSampleCount) {

    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(db);
    checkNotNull(nodeId);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.nodeId = nodeId;
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
    if (dataColumnSidecar.getSlot().isGreaterThanOrEqualTo(getFirstIncompleteSlot())
        && !getMyCustody(dataColumnSidecar.getSlot()).contains(dataColumnSidecar.getIndex())) {
      collectedSamples.compute(
          dataColumnSidecar.getSlot(),
          (__, dataColumnIdentifiers) -> {
            if (dataColumnIdentifiers != null) {
              dataColumnIdentifiers.add(DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
              return dataColumnIdentifiers;
            } else {
              Set<DataColumnIdentifier> collectedIdentifiers = new HashSet<>();
              collectedIdentifiers.add(DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
              return collectedIdentifiers;
            }
          });
      if (assignedSampleColumns.containsKey(dataColumnSidecar.getSlot())
          && assignedSampleColumns.get(dataColumnSidecar.getSlot()).stream()
              .map(
                  assignedIndex ->
                      collectedSamples.get(dataColumnSidecar.getSlot()).stream()
                          .map(DataColumnIdentifier::getIndex)
                          .filter(collectedIndex -> collectedIndex.equals(assignedIndex))
                          .map(__ -> true)
                          .findFirst()
                          .orElse(false))
              .filter(columnResult -> columnResult.equals(false))
              .findFirst()
              .orElse(true)) {
        LOG.info("Slot {} sampling is completed successfully", dataColumnSidecar.getSlot());
      }
    }
  }

  private synchronized List<UInt64> getSampleColumns(UInt64 slot) {
    final boolean alreadyAssigned = assignedSampleColumns.containsKey(slot);
    final List<UInt64> assignedSampleColumns =
        this.assignedSampleColumns.computeIfAbsent(slot, this::computeSampleColumns);
    if (!alreadyAssigned) {
      LOG.info("For slot {} assigned columns: {}", slot, assignedSampleColumns);
    }

    return assignedSampleColumns;
  }

  private List<UInt64> computeSampleColumns(UInt64 slot) {
    final int columnsCount =
        SpecConfigEip7594.required(spec.atSlot(slot).getConfig()).getNumberOfColumns();
    final List<UInt64> myCustodyColumns = getMyCustody(slot);

    final List<UInt64> assignedSamples = new ArrayList<>();
    while (assignedSamples.size() < myDataColumnSampleCount) {
      final UInt64 candidate = UInt64.valueOf(rnd.nextInt(columnsCount));
      if (myCustodyColumns.contains(candidate) || assignedSamples.contains(candidate)) {
        continue;
      }
      assignedSamples.add(candidate);
    }

    return assignedSamples;
  }

  private List<UInt64> getMyCustody(UInt64 slot) {
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    return spec.atEpoch(epoch)
        .miscHelpers()
        .toVersionEip7594()
        .map(
            miscHelpersEip7594 ->
                miscHelpersEip7594.computeCustodyColumnIndexes(nodeId, myDataColumnSampleCount))
        .orElse(List.of());
  }

  @Override
  public void onSlot(UInt64 slot) {
    currentSlot = slot;
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch());
  }

  private void advanceFirstIncompleteSlot(UInt64 finalizedEpoch) {
    UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());

    streamPotentiallyIncompleteSlotSamples()
        // will move FirstIncompleteSlot only to finalized slots
        .takeWhile(sc -> sc.slot().isLessThan(firstNonFinalizedSlot))
        .map(scan(CompleteIncompleteSlot.ZERO, CompleteIncompleteSlot::add))
        .takeWhile(c -> c.firstIncomplete() == null)
        .reduce((a, b) -> b) // takeLast()
        .flatMap(CompleteIncompleteSlot::getFirstIncompleteSlot)
        .ifPresent(
            slot -> {
              db.setFirstSamplerIncompleteSlot(slot);
              prune(slot);
            });
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

  private Stream<SlotColumnsTask> streamPotentiallyIncompleteSlotSamples() {
    if (currentSlot == null) {
      return Stream.empty();
    }

    UInt64 firstIncompleteSlot = getFirstIncompleteSlot();

    return Stream.iterate(
            firstIncompleteSlot, slot -> slot.isLessThanOrEqualTo(currentSlot), UInt64::increment)
        .map(
            slot -> {
              final Optional<Bytes32> maybeCanonicalBlockRoot = getBlockRootIfHaveBlobs(slot);
              final List<UInt64> requiredColumns = getSampleColumns(slot);
              return new SlotColumnsTask(
                  slot,
                  maybeCanonicalBlockRoot,
                  requiredColumns,
                  collectedSamples.getOrDefault(slot, Collections.emptySet()));
            });
  }

  private UInt64 getFirstIncompleteSlot() {
    return db.getFirstSamplerIncompleteSlot().orElseGet(() -> getEarliestSampleSlot(currentSlot));
  }

  private Optional<Bytes32> getBlockRootIfHaveBlobs(UInt64 slot) {
    return blockResolver
        .getBlockAtSlot(slot)
        .filter(
            block ->
                block
                        .getBeaconBlock()
                        .flatMap(b -> b.getBody().toVersionEip7594())
                        .map(b -> b.getBlobKzgCommitments().size())
                        .orElse(0)
                    > 0)
        .map(BeaconBlock::getRoot);
  }

  @Override
  public Stream<ColumnSlotAndIdentifier> streamMissingColumns() {
    return streamPotentiallyIncompleteSlotSamples()
        .flatMap(
            samplingSlot ->
                samplingSlot.getIncompleteColumns().stream()
                    .map(colId -> new ColumnSlotAndIdentifier(samplingSlot.slot(), colId)));
  }
}
