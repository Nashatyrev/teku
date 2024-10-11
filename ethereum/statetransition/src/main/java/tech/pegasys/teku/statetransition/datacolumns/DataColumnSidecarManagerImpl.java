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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DataColumnSidecarManagerImpl implements DataColumnSidecarManager {
  private static final Logger LOG = LogManager.getLogger();
  private final DataColumnSidecarGossipValidator validator;
  private final Subscribers<ValidDataColumnSidecarsListener> validDataColumnSidecarsSubscribers =
      Subscribers.create(true);
  private final LabelledMetric<OperationTimer> dataColumnSidecarGossipVerificationTimer;

  public DataColumnSidecarManagerImpl(
      DataColumnSidecarGossipValidator validator, MetricsSystem metricsSystem) {
    this.validator = validator;
    this.dataColumnSidecarGossipVerificationTimer =
        metricsSystem.createSimpleLabelledTimer(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_gossip_verification_seconds",
            "Full runtime of data column sidecars gossip verification");
  }

  @Override
  public SafeFuture<InternalValidationResult> onDataColumnSidecarGossip(
      DataColumnSidecar dataColumnSidecar, Optional<UInt64> arrivalTimestamp) {
    SafeFuture<InternalValidationResult> validation;

    try (OperationTimer.TimingContext ignored =
        dataColumnSidecarGossipVerificationTimer.labels().startTimer()) {
      validation = validator.validate(dataColumnSidecar);
    } catch (final Throwable t) {
      LOG.error("Failed to start data column sidecar gossip validation metric timer.", t);
      return SafeFuture.completedFuture(InternalValidationResult.reject("error"));
    }

    return validation.thenPeek(
        res -> {
          if (res.isAccept()) {
            validDataColumnSidecarsSubscribers.forEach(
                listener -> listener.onNewValidSidecar(dataColumnSidecar));
          }
        });
  }

  @Override
  public void onDataColumnSidecarPublish(DataColumnSidecar sidecar) {
    validDataColumnSidecarsSubscribers.forEach(l -> l.onNewValidSidecar(sidecar));
  }

  @Override
  public void subscribeToValidDataColumnSidecars(ValidDataColumnSidecarsListener sidecarsListener) {
    validDataColumnSidecarsSubscribers.subscribe(sidecarsListener);
  }
}
