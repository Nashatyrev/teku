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

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.BlockBlobChecker;
import tech.pegasys.teku.statetransition.datacolumns.CustodyFunction;

class MissingDasColumnsDbImpl extends AbstractDelegatingDasDb implements MissingDasColumnsDb {

  private final BlockBlobChecker blockBlobChecker;
  private final CustodyFunction custodyFunction;
  private final ColumnIdCachingDasDb columnIdCachingDasDb;

  public MissingDasColumnsDbImpl(
      ColumnIdCachingDasDb delegateDb,
      BlockBlobChecker blockBlobChecker,
      CustodyFunction custodyFunction) {
    super(delegateDb);
    this.blockBlobChecker = blockBlobChecker;
    this.custodyFunction = custodyFunction;
    this.columnIdCachingDasDb = delegateDb;
  }

  @Override
  public SafeFuture<Set<UInt64>> getMissingColumnIndexes(SlotAndBlockRoot blockId) {
    SafeFuture<Set<UInt64>> expectedCustodyFuture =
        doesBlockHasBlobs(blockId)
            .thenApply(
                hasBlobs -> {
                  if (hasBlobs) {
                    return custodyFunction.getCustodyColumns(blockId.getSlot());
                  } else {
                    return Collections.emptySet();
                  }
                });
    SafeFuture<Set<UInt64>> existingCustodyFuture = getColumnIndexes(blockId);
    return expectedCustodyFuture.thenCombine(existingCustodyFuture, Sets::difference);
  }

  private SafeFuture<Boolean> doesBlockHasBlobs(SlotAndBlockRoot blockId) {
    if (columnIdCachingDasDb.hasAnyColumnsCached(blockId)) {
      // fast track
      return SafeFuture.completedFuture(true);
    } else {
      return blockBlobChecker.hasBlobs(blockId);
    }
  }

  private SafeFuture<Set<UInt64>> getColumnIndexes(SlotAndBlockRoot blockId) {
    return columnIdCachingDasDb.getBlockCache(blockId).getColumnIndexes();
  }

  @Override
  public void pruneCaches(UInt64 tillSlot) {
    columnIdCachingDasDb.pruneCaches(tillSlot);
  }
}
