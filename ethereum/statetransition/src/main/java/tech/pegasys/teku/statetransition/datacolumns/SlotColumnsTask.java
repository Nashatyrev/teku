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

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public record SlotColumnsTask(
    UInt64 slot,
    Optional<Bytes32> canonicalBlockRoot,
    Collection<UInt64> requiredColumnIndices,
    Collection<DataColumnIdentifier> collectedColumnIndices) {
  public Collection<DataColumnIdentifier> getIncompleteColumns() {
    return canonicalBlockRoot
        .map(
            blockRoot -> {
              Set<UInt64> collectedIndices =
                  collectedColumnIndices.stream()
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

  public boolean isComplete() {
    return canonicalBlockRoot().isPresent() && !isIncomplete();
  }

  public boolean isIncomplete() {
    return !getIncompleteColumns().isEmpty();
  }
}
