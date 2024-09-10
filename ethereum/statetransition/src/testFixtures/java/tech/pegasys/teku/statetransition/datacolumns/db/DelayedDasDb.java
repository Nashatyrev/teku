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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;

public class DelayedDasDb implements DataColumnSidecarDB {
  private final DataColumnSidecarDB delegate;
  private final AsyncRunner asyncRunner;
  private final Duration delay;

  public DelayedDasDb(DataColumnSidecarDB delegate, AsyncRunner asyncRunner, Duration delay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.delay = delay;
  }

  private <T> SafeFuture<T> delay(SafeFuture<T> fut) {
    return fut.thenCompose(r -> asyncRunner.getDelayedFuture(delay).thenApply(__ -> r));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delay(delegate.getFirstCustodyIncompleteSlot());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delay(delegate.getFirstSamplerIncompleteSlot());
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return delay(delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(ColumnSlotAndIdentifier identifier) {
    return delay(delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return delay(delegate.getColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    return delay(delegate.setFirstCustodyIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delay(delegate.setFirstSamplerIncompleteSlot(slot));
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    delegate.addSidecar(sidecar);
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    delegate.pruneAllSidecars(tillSlot);
  }
}
