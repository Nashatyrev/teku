/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku;

import io.vertx.core.Vertx;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.ExecutorServiceFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.OccurrenceCounter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class NodeServiceConfigBuilder {

  protected final TekuConfiguration tekuConfig;
  protected Vertx vertx;
  protected MetricsEndpoint metricsEndpoint;
  protected TekuDefaultExceptionHandler subscriberExceptionHandler;
  protected OccurrenceCounter rejectedExecutionCounter;
  protected TimeProvider timeProvider;
  protected ExecutorServiceFactory executorFactory;
  protected EventChannels eventChannels;
  protected AsyncRunnerFactory asyncRunnerFactory;
  protected DataDirLayout dataDirLayout;

  public NodeServiceConfigBuilder(TekuConfiguration tekuConfig) {
    this.tekuConfig = tekuConfig;
  }

  public void initMissingDefaults() {
    if (vertx == null) {
      vertx = Vertx.vertx();
    }
    if (metricsEndpoint == null) {
      metricsEndpoint = new MetricsEndpoint(tekuConfig.metricsConfig(), vertx);
    }
    if (subscriberExceptionHandler == null) {
      subscriberExceptionHandler = new TekuDefaultExceptionHandler();
    }
    if (rejectedExecutionCounter == null) {
      rejectedExecutionCounter = new OccurrenceCounter(120);
    }
    if (timeProvider == null) {
      timeProvider = new SystemTimeProvider();
    }
    if (executorFactory == null) {
      executorFactory = createExecutorFactory();
    }
    if (eventChannels == null) {
      eventChannels = createEventChannels();
    }
    if (asyncRunnerFactory == null) {
      asyncRunnerFactory = AsyncRunnerFactory.createDefault(executorFactory);
    }
    if (dataDirLayout == null) {
      dataDirLayout = DataDirLayout.createFrom(tekuConfig.dataConfig());
    }
  }

  protected ExecutorServiceFactory createExecutorFactory() {
    return new MetricTrackingExecutorFactory(metricsEndpoint.getMetricsSystem(), rejectedExecutionCounter);
  }

  protected EventChannels createEventChannels() {
    return new EventChannels(subscriberExceptionHandler, executorFactory, metricsEndpoint.getMetricsSystem());
  }

  public NodeServiceConfigBuilder timeProvider(TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public NodeServiceConfigBuilder executorFactory(ExecutorServiceFactory executorFactory) {
    this.executorFactory = executorFactory;
    return this;
  }

  public NodeServiceConfigBuilder eventChannels(EventChannels eventChannels) {
    this.eventChannels = eventChannels;
    return this;
  }

  // TODO more setters

  public ServiceConfig build() {
    initMissingDefaults();

    return new ServiceConfig(
        asyncRunnerFactory,
        timeProvider,
        eventChannels,
        metricsEndpoint,
        dataDirLayout,
        rejectedExecutionCounter);
  }
}
