package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

@Module
public interface PowModule {

  @Qualifier
  @interface ProposerDefaultFeeRecipient{

}

  @Provides
  @Singleton
  static Optional<TerminalPowBlockMonitor> provideTerminalPowBlockMonitor(
      Spec spec,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      TimeProvider timeProvider,
      ExecutionLayerChannel executionLayer,
      RecentChainData recentChainData,
      ForkChoiceNotifier forkChoiceNotifier,
      EventLogger eventLogger) {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(
          new TerminalPowBlockMonitor(
              executionLayer,
              spec,
              recentChainData,
              forkChoiceNotifier,
              beaconAsyncRunner,
              eventLogger,
              timeProvider));
    } else {
      return Optional.empty();
    }
  }

  @Provides
  @Singleton
  static Eth1DataCache provideEth1DataCache(Spec spec, MetricsSystem metricsSystem) {
    return new Eth1DataCache(spec, metricsSystem, new Eth1VotingPeriod(spec));
  }

  @Provides
  @Singleton
  static DepositProvider provideDepositProvider(
      Spec spec,
      PowchainConfiguration powchainConfig,
      MetricsSystem metricsSystem,
      RecentChainData recentChainData,
      Eth1DataCache eth1DataCache,
      StorageUpdateChannel storageUpdateChannel,
      Eth1DepositStorageChannel eth1DepositStorageChannel,
      EventChannelSubscriber<Eth1EventsChannel> eth1EventsChannelSubscriber,
      EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      EventLogger eventLogger
  ) {
    DepositProvider depositProvider =
        new DepositProvider(
            metricsSystem,
            recentChainData,
            eth1DataCache,
            storageUpdateChannel,
            eth1DepositStorageChannel,
            spec,
            eventLogger,
            powchainConfig.useMissingDepositEventLogging());
    eth1EventsChannelSubscriber.subscribe(depositProvider);
    finalizedCheckpointChannelSubscriber.subscribe(depositProvider);
    slotEventsChannelSubscriber.subscribe(depositProvider);

    return depositProvider;
  }

  @Provides
  @Singleton
  @ProposerDefaultFeeRecipient
  static Optional<Eth1Address> proposerDefaultFeeRecipient(
      Spec spec,
      ValidatorConfig validatorConfig,
      BeaconRestApiConfig restApiConfig,
      StatusLogger statusLogger
  ) {
    if (!spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(Eth1Address.ZERO);
    }

    final Optional<Eth1Address> defaultFeeRecipient =
        validatorConfig.getProposerDefaultFeeRecipient();

    if (defaultFeeRecipient.isEmpty() && restApiConfig.isRestApiEnabled()) {
      statusLogger.warnMissingProposerDefaultFeeRecipientWithRestAPIEnabled();
    }

    return defaultFeeRecipient;
  }

}
