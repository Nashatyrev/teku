/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.service.serviceutils;

import java.util.function.IntSupplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.OccurrenceCounter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class ServiceConfig {

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final TimeProvider timeProvider;
  private final EventChannels eventChannels;
  private final MetricsEndpoint metricsEndpoint;
  private final DataDirLayout dataDirLayout;

  private final OccurrenceCounter rejectedExecutionCounter;

  public ServiceConfig(
      final AsyncRunnerFactory asyncRunnerFactory,
      final TimeProvider timeProvider,
      final EventChannels eventChannels,
      final MetricsEndpoint metricsEndpoint,
      final DataDirLayout dataDirLayout,
      final OccurrenceCounter rejectedExecutionCounter) {
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.timeProvider = timeProvider;
    this.eventChannels = eventChannels;
    this.metricsEndpoint = metricsEndpoint;
    this.dataDirLayout = dataDirLayout;
    this.rejectedExecutionCounter = rejectedExecutionCounter;
  }

  public TimeProvider getTimeProvider() {
    return timeProvider;
  }

  public EventChannels getEventChannels() {
    return eventChannels;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsEndpoint.getMetricsSystem();
  }

  public MetricsEndpoint getMetricsEndpoint() {
    return metricsEndpoint;
  }

  public DataDirLayout getDataDirLayout() {
    return dataDirLayout;
  }

  public IntSupplier getRejectedExecutionsSupplier() {
    return rejectedExecutionCounter::getTotalCount;
  }

  public OccurrenceCounter getRejectedExecutionCounter() {
    return rejectedExecutionCounter;
  }

  public AsyncRunnerFactory getAsyncRunnerFactory() {
    return asyncRunnerFactory;
  }
}
