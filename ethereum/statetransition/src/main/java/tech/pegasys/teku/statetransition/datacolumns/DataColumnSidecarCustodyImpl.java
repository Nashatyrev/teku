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

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.DasColumnDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.util.rx.AsyncIterator;

public class DataColumnSidecarCustodyImpl implements UpdatableDataColumnSidecarCustody {

  // for how long the custody will wait for a missing column to be gossiped
  private final int gossipWaitSlots = 2;

  private final Spec spec;
  private final DasColumnDbAccessor db;
  private final CanonicalBlockRootResolver blockResolver;
  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;
  private final CurrentSlotListener.Supplier currentSlotSupplier;

  public DataColumnSidecarCustodyImpl(
      Spec spec,
      CanonicalBlockRootResolver blockResolver,
      DasColumnDbAccessor db,
      CurrentSlotListener.Supplier currentSlotSupplier,
      UInt256 nodeId,
      int totalCustodySubnetCount) {

    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(db);
    checkNotNull(currentSlotSupplier);
    checkNotNull(nodeId);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
    this.currentSlotSupplier = currentSlotSupplier;
  }

  @Override
  public void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    if (isMyCustody(dataColumnSidecar.getSlot(), dataColumnSidecar.getIndex())) {
      db.addSidecar(dataColumnSidecar);
    }
  }

  private boolean isMyCustody(UInt64 slot, UInt64 columnIndex) {
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    return spec.atEpoch(epoch)
        .miscHelpers()
        .toVersionEip7594()
        .map(
            miscHelpersEip7594 ->
                miscHelpersEip7594
                    .computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount)
                    .contains(columnIndex))
        .orElse(false);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnIdentifier columnId) {
    return db.getSidecar(columnId);
  }

  private SafeFuture<Set<ColumnSlotAndIdentifier>> retrieveMissingColumnsForSlot(UInt64 slot) {
    return blockResolver
        .getBlockRootAtSlot(slot)
        .thenCompose(
            maybeBlockRoot ->
                maybeBlockRoot
                    .map(blockRoot -> db.getMissingColumnIds(new SlotAndBlockRoot(slot, blockRoot)))
                    .orElse(SafeFuture.completedFuture(Collections.emptySet())));
  }

  private AsyncIterator<ColumnSlotAndIdentifier> iterateMissingColumns(
      UInt64 fromSlot, UInt64 toSlotIncluded) {
    Iterator<SafeFuture<Set<ColumnSlotAndIdentifier>>> missingColumnFutureIterator =
        Stream.iterate(
                fromSlot, slot -> slot.isLessThanOrEqualTo(toSlotIncluded), UInt64::increment)
            .map(this::retrieveMissingColumnsForSlot)
            .iterator();
    return AsyncIterator.createOneShot(missingColumnFutureIterator).flatten(set -> set);
  }

  @Override
  public AsyncIterator<ColumnSlotAndIdentifier> iterateMissingColumns() {
    SafeFuture<AsyncIterator<ColumnSlotAndIdentifier>> asyncIteratorPromise =
        db.getOrCalculateFirstCustodyIncompleteSlot()
            .thenApply(
                firstIncompleteSlot -> {
                  UInt64 toSlotIncluded = currentSlotSupplier.getCurrentSlot().minusMinZero(gossipWaitSlots);
                  return iterateMissingColumns(firstIncompleteSlot, toSlotIncluded);
                });
    return AsyncIterator.awaitIterator(asyncIteratorPromise);
  }
}
