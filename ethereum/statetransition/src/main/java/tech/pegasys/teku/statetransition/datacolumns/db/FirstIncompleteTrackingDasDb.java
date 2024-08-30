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

package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockRootResolver;
import tech.pegasys.teku.statetransition.datacolumns.FinalizedSlotListener;

class FirstIncompleteTrackingDasDb implements DasColumnDbAccessor {

  private final MissingDasColumnsDb missingDb;
  private final CanonicalBlockRootResolver blockRootResolver;

  private volatile Optional<UInt64> cachedFirstCustodyIncompleteSlot;
  private final SafeFuture<UInt64> firstCustodyIncompleteSlotFuture;
  private volatile UInt64 finalizedSlot;

  public FirstIncompleteTrackingDasDb(
      MissingDasColumnsDb delegateDb,
      CanonicalBlockRootResolver blockRootResolver,
      FinalizedSlotListener.Subscriber finalizedSlotSubscriber,
      UInt64 currentMinCustodySlot) {
    this.missingDb = delegateDb;
    this.blockRootResolver = blockRootResolver;
    this.firstCustodyIncompleteSlotFuture =
        this.missingDb
            .getFirstCustodyIncompleteSlot()
            .thenApply(mayBeSlot -> mayBeSlot.orElse(currentMinCustodySlot))
            .thenPeek(slot -> cachedFirstCustodyIncompleteSlot = Optional.of(slot));
    finalizedSlot = finalizedSlotSubscriber.subscribeToFinalizedSlots(this::onNewFinalizedSlot);
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    missingDb.addSidecar(sidecar);
    cachedFirstCustodyIncompleteSlot.ifPresent(
        slot -> {
          if (sidecar.getSlot().equals(slot)) {
            maybeAdvanceFirstCustodyIncompleteSlot(slot);
          }
        });
  }

  public void onNewFinalizedSlot(UInt64 newFinalizedSlot) {
    if (newFinalizedSlot.isGreaterThan(finalizedSlot)) {
      finalizedSlot = newFinalizedSlot;
      maybeAdvanceFirstCustodyIncompleteSlot();
    }
  }

  private void maybeAdvanceFirstCustodyIncompleteSlot() {
    cachedFirstCustodyIncompleteSlot.ifPresent(this::maybeAdvanceFirstCustodyIncompleteSlot);
  }

  private void maybeAdvanceFirstCustodyIncompleteSlot(UInt64 curFirstCustodyIncompleteSlot) {
    if (curFirstCustodyIncompleteSlot.isGreaterThanOrEqualTo(finalizedSlot)) {
      return;
    }

    blockRootResolver
        .getBlockRootAtSlot(curFirstCustodyIncompleteSlot)
        .thenCompose(
            maybeBlockRoot ->
                maybeBlockRoot
                    .map(
                        blockRoot ->
                            missingDb.getMissingColumnIndexes(
                                new SlotAndBlockRoot(curFirstCustodyIncompleteSlot, blockRoot)))
                    .orElse(SafeFuture.completedFuture(Collections.emptySet())))
        .thenAccept(
            missingIndexes -> {
              if (missingIndexes.isEmpty()) {
                advanceFirstCustodyIncompleteSlot(curFirstCustodyIncompleteSlot);
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void advanceFirstCustodyIncompleteSlot(UInt64 curFirstCustodyIncompleteSlot) {
    UInt64 nextIncompleteSlot = curFirstCustodyIncompleteSlot.increment();
    setFirstCustodyIncompleteSlotImpl(nextIncompleteSlot);
    maybeAdvanceFirstCustodyIncompleteSlot(nextIncompleteSlot);
    missingDb.pruneCaches(curFirstCustodyIncompleteSlot);
  }

  private void setFirstCustodyIncompleteSlotImpl(UInt64 slot) {
    missingDb.setFirstSamplerIncompleteSlot(slot).ifExceptionGetsHereRaiseABug();
    cachedFirstCustodyIncompleteSlot = Optional.of(slot);
  }

  @Override
  public SafeFuture<UInt64> getOrCalculateFirstCustodyIncompleteSlot() {
    return cachedFirstCustodyIncompleteSlot
        .map(SafeFuture::completedFuture)
        .orElse(firstCustodyIncompleteSlotFuture);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return missingDb.getSidecar(identifier);
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return missingDb.getColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Set<UInt64>> getMissingColumnIndexes(SlotAndBlockRoot blockId) {
    return missingDb.getMissingColumnIndexes(blockId);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return missingDb.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return missingDb.getFirstSamplerIncompleteSlot();
  }
}
