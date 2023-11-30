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

package tech.pegasys.teku;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.MAX_EPOCHS_STORE_BLOBS;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import oshi.SystemInfo;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.data.publisher.MetricsPublisherManager;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.logging.StartupLogConfig;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.ServiceController;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public abstract class AbstractNode implements Node {
  private static final Logger LOG = LogManager.getLogger();

  protected final ServiceConfig serviceConfig;

  private final AsyncRunner asyncRunner;
  private final MetricsPublisherManager metricsPublisher;

  private Optional<Cancellable> counterMaintainer = Optional.empty();

  protected AbstractNode(final TekuConfiguration tekuConfig, ServiceConfig serviceConfig) {
    setupStatusLog(tekuConfig);
    reportOverrides(tekuConfig);

    this.serviceConfig = serviceConfig;

    this.metricsPublisher =
        new MetricsPublisherManager(
            serviceConfig.getAsyncRunnerFactory(),
            serviceConfig.getTimeProvider(),
            serviceConfig.getMetricsEndpoint(),
            serviceConfig.getDataDirLayout().getBeaconDataDirectory().toFile());

    this.asyncRunner = serviceConfig.getAsyncRunnerFactory().create("AbstractNode", 1);
  }

  private void setupStatusLog(TekuConfiguration tekuConfig) {
    final String network =
        tekuConfig
            .eth2NetworkConfiguration()
            .getEth2Network()
            .map(Eth2Network::configName)
            .orElse("empty");
    final String storageMode = tekuConfig.storageConfiguration().getDataStorageMode().name();
    final BeaconRestApiConfig beaconChainRestApiConfig =
        tekuConfig.beaconChain().beaconRestApiConfig();
    final ValidatorRestApiConfig validatorRestApiConfig = tekuConfig.validatorRestApiConfig();

    STATUS_LOG.onStartup(VersionProvider.VERSION);
    STATUS_LOG.startupConfigurations(
        StartupLogConfig.builder()
            .network(network)
            .storageMode(storageMode)
            .hardwareInfo(new SystemInfo().getHardware())
            .beaconChainRestApiEnabled(beaconChainRestApiConfig.isRestApiEnabled())
            .beaconChainRestApiInterface(beaconChainRestApiConfig.getRestApiInterface())
            .beaconChainRestApiPort(beaconChainRestApiConfig.getRestApiPort())
            .beaconChainRestApiAllow(beaconChainRestApiConfig.getRestApiHostAllowlist())
            .validatorRestApiInterface(validatorRestApiConfig.getRestApiInterface())
            .validatorRestApiPort(validatorRestApiConfig.getRestApiPort())
            .validatorRestApiAllow(validatorRestApiConfig.getRestApiHostAllowlist())
            .build());
  }

  private void reportOverrides(final TekuConfiguration tekuConfig) {

    for (SpecMilestone specMilestone : SpecMilestone.values()) {
      tekuConfig
          .eth2NetworkConfiguration()
          .getForkEpoch(specMilestone)
          .ifPresent(forkEpoch -> STATUS_LOG.warnForkEpochChanged(specMilestone.name(), forkEpoch));
    }

    tekuConfig
        .eth2NetworkConfiguration()
        .getTotalTerminalDifficultyOverride()
        .ifPresent(
            ttdo ->
                STATUS_LOG.warnBellatrixParameterChanged(
                    "TERMINAL_TOTAL_DIFFICULTY", ttdo.toString()));

    tekuConfig
        .eth2NetworkConfiguration()
        .getTerminalBlockHashOverride()
        .ifPresent(
            tbho ->
                STATUS_LOG.warnBellatrixParameterChanged("TERMINAL_BLOCK_HASH", tbho.toString()));

    tekuConfig
        .eth2NetworkConfiguration()
        .getTerminalBlockHashEpochOverride()
        .ifPresent(
            tbheo ->
                STATUS_LOG.warnBellatrixParameterChanged(
                    "TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH", tbheo.toString()));

    // Deneb's epochsStoreBlobs warning, which is not actually a full override
    final Optional<SpecVersion> specVersionDeneb =
        Optional.ofNullable(
            tekuConfig.eth2NetworkConfiguration().getSpec().forMilestone(SpecMilestone.DENEB));
    specVersionDeneb
        .flatMap(__ -> tekuConfig.eth2NetworkConfiguration().getEpochsStoreBlobs())
        .ifPresent(
            epochsStoreBlobsInput -> {
              final SpecConfigDeneb specConfigDeneb =
                  SpecConfigDeneb.required(specVersionDeneb.get().getConfig());
              STATUS_LOG.warnDenebEpochsStoreBlobsParameterSet(
                  epochsStoreBlobsInput.toString(),
                  specConfigDeneb.getEpochsStoreBlobs()
                      != specConfigDeneb.getMinEpochsForBlobSidecarsRequests(),
                  String.valueOf(specConfigDeneb.getMinEpochsForBlobSidecarsRequests()),
                  MAX_EPOCHS_STORE_BLOBS);
            });
  }

  @Override
  public abstract ServiceController getServiceController();

  @Override
  public void start() {
    serviceConfig.getMetricsEndpoint().start().join();
    metricsPublisher.start().join();
    getServiceController().start().join();
    counterMaintainer =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::pollRejectedExecutions,
                Duration.ofSeconds(5),
                (err) -> LOG.debug("rejected execution poll failed", err)));
  }

  private void pollRejectedExecutions() {
    final int rejectedExecutions = serviceConfig.getRejectedExecutionCounter().poll();
    if (rejectedExecutions > 0) {
      LOG.trace("Rejected execution count from last 5 seconds: " + rejectedExecutions);
    }
  }

  @Override
  public void stop() {
    // Stop processing new events
    serviceConfig
        .getEventChannels()
        .stop()
        .orTimeout(asyncRunner, 30, TimeUnit.SECONDS)
        .handleException(error -> LOG.warn("Failed to stop event channels cleanly", error))
        .join();

    counterMaintainer.ifPresent(Cancellable::cancel);

    // Stop async actions
    serviceConfig.getAsyncRunnerFactory().shutdown();

    // Stop services. This includes closing the database.
    getServiceController()
        .stop()
        .orTimeout(asyncRunner, 30, TimeUnit.SECONDS)
        .handleException(error -> LOG.error("Failed to stop services", error))
        .thenCompose(__ -> serviceConfig.getMetricsEndpoint().stop())
        .orTimeout(5, TimeUnit.SECONDS)
        .handleException(error -> LOG.debug("Failed to stop metrics", error))
        .join();
  }
}
