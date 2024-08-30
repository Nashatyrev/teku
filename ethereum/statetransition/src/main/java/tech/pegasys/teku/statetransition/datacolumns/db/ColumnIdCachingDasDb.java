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

import java.util.BitSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.BlockBlobChecker;
import tech.pegasys.teku.statetransition.datacolumns.CustodyFunction;

public interface ColumnIdCachingDasDb extends DataColumnSidecarDB {

  Optional<BlockCache> getCached(SlotAndBlockRoot blockId);

  BlockCache getBlockCache(SlotAndBlockRoot blockId);

  default boolean hasAnyColumnsCached(SlotAndBlockRoot blockId) {
    return getCached(blockId).map(BlockCache::hasAnyCached).orElse(false);
  }

  void pruneCaches(UInt64 tillSlot);

  interface BlockCache {

    BitSet getCachedColumns();

    SafeFuture<BitSet> getColumnsBitmap();

    default SafeFuture<Set<UInt64>> getColumnIndexes() {
      return getColumnsBitmap()
          .thenApply(
              bitmap -> bitmap.stream().mapToObj(UInt64::valueOf).collect(Collectors.toSet()));
    }

    default boolean hasAnyCached() {
      return !getCachedColumns().isEmpty();
    }
  }

  default MissingDasColumnsDb withMissingColumnsTracking(
      BlockBlobChecker blockBlobChecker, CustodyFunction custodyFunction) {
    return new MissingDasColumnsDbImpl(this, blockBlobChecker, custodyFunction);
  }
}
