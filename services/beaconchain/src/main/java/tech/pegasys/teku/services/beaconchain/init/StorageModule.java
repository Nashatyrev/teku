package tech.pegasys.teku.services.beaconchain.init;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.beaconchain.SlotProcessor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.BeaconModule.GenesisTimeTracker;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.CurrentSlotProvider;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityPeriodValidator;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityStoreChainValidator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.EarliestAvailableBlockSlot;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

@Module
public interface StorageModule {

  String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  interface OnStoreInitializedHandler {
    void handle();
  }

  @Binds
  StorageQueryChannel bindStorageQueryChannel(CombinedStorageChannel combinedStorageChannel);

  @Binds
  StorageUpdateChannel bindStorageUpdateChannel(CombinedStorageChannel combinedStorageChannel);

  @Provides
  @Singleton
  static KeyValueStore<String, Bytes> keyValueStore(DataDirLayout dataDirLayout) {
    return new FileKeyValueStore(
        dataDirLayout.getBeaconDataDirectory().resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  @Provides
  @Singleton
  static EarliestAvailableBlockSlot earliestAvailableBlockSlot(
      StoreConfig storeConfig,
      TimeProvider timeProvider,
      StorageQueryChannel storageQueryChannel) {
    return new EarliestAvailableBlockSlot(
        storageQueryChannel, timeProvider, storeConfig.getEarliestAvailableBlockSlotFrequency());
  }

  @Provides
  @Singleton
  static CombinedChainDataClient combinedChainDataClient(
      Spec spec,
      StorageQueryChannel storageQueryChannel,
      RecentChainData recentChainData,
      EarliestAvailableBlockSlot earliestAvailableBlockSlot) {
    return new CombinedChainDataClient(
        recentChainData, storageQueryChannel, spec, earliestAvailableBlockSlot);
  }

  @Provides
  @Singleton
  static SafeFuture<RecentChainData> recentChainDataFuture(
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      TimeProvider timeProvider,
      MetricsSystem metricsSystem,
      Spec spec,
      StoreConfig storeConfig,
      StorageQueryChannel storageQueryChannel,
      StorageUpdateChannel storageUpdateChannel,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      VoteUpdateChannel voteUpdateChannel,
      FinalizedCheckpointChannel finalizedCheckpointChannel,
      ChainHeadChannel chainHeadChannel,
      ValidatorIsConnectedProvider validatorIsConnectedProvider) {

    return StorageBackedRecentChainData.create(
        metricsSystem,
        storeConfig,
        beaconAsyncRunner,
        timeProvider,
        blockBlobSidecarsTrackersPool::getBlock,
        blockBlobSidecarsTrackersPool::getBlobSidecar,
        storageQueryChannel,
        storageUpdateChannel,
        voteUpdateChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        validatorIsConnectedProvider,
        spec);
  }

  @Provides
  @Singleton
  // TODO producer ?
  static RecentChainData recentChainData(
      Eth2NetworkConfiguration eth2NetworkConfig,
      SafeFuture<RecentChainData> recentChainDataFuture,
      StatusLogger statusLogger,
      Lazy<WeakSubjectivityPeriodValidator> weakSubjectivityPeriodValidator,
      Lazy<RecentChainDataStateInitializer> recentChainDataStateInitializer,
      OnStoreInitializedHandler onStoreInitializedHandler) {

    RecentChainData recentChainData = recentChainDataFuture.join();

    boolean isAllowSyncOutsideWeakSubjectivityPeriod =
        eth2NetworkConfig.getNetworkBoostrapConfig().isAllowSyncOutsideWeakSubjectivityPeriod();
    boolean isUsingCustomInitialState =
        eth2NetworkConfig.getNetworkBoostrapConfig().isUsingCustomInitialState();

    if (isAllowSyncOutsideWeakSubjectivityPeriod) {
      statusLogger.warnIgnoringWeakSubjectivityPeriod();
    }

    // Setup chain storage
    if (recentChainData.isPreGenesis()) {
      recentChainDataStateInitializer.get().setupInitialState(recentChainData);
    } else {
      if (isUsingCustomInitialState) {
        statusLogger.warnInitialStateIgnored();
      }
      if (!isAllowSyncOutsideWeakSubjectivityPeriod) {
        weakSubjectivityPeriodValidator.get().validate(recentChainData);
      }
    }

    recentChainData.subscribeStoreInitialized(onStoreInitializedHandler::handle);

    return recentChainData;
  }

  @Provides
  @Singleton
  static OnStoreInitializedHandler onStoreInitializedHandler(
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      CurrentSlotProvider currentSlotProvider,
      Lazy<WeakSubjectivityStoreChainValidator> weakSubjectivityStoreChainValidator,
      Lazy<GenesisTimeTracker> genesisTimeTracker,
      SlotProcessor slotProcessor,
      PerformanceTracker performanceTracker
  ) {
    return () -> {
      UInt64 genesisTime = recentChainData.getGenesisTime();
      UInt64 currentTime = timeProvider.getTimeInSeconds();
      final UInt64 currentSlot = currentSlotProvider.getCurrentSlot(genesisTime, currentTime);
      if (currentTime.compareTo(genesisTime) >= 0) {
        // Validate that we're running within the weak subjectivity period
        weakSubjectivityStoreChainValidator.get().validate(currentSlot);
      } else {
        genesisTimeTracker.get().update();
      }
      slotProcessor.setCurrentSlot(currentSlot);
      performanceTracker.start(currentSlot);
    };
  }
}
