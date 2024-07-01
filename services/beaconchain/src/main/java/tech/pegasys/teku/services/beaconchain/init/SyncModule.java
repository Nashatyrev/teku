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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import java.time.Duration;
import java.util.Optional;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.DefaultSyncServiceFactory;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.SyncServiceFactory;
import tech.pegasys.teku.beacon.sync.events.CoalescingChainHeadChannel;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.DepositProvider;

@Module
public interface SyncModule {

  @Provides
  @Singleton
  static SyncServiceFactory syncServiceFactory(
      final Spec spec,
      final SyncConfig syncConfig,
      final Eth2NetworkConfiguration eth2NetworkConfig,
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      @BeaconAsyncRunner final AsyncRunner beaconAsyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final StorageUpdateChannel storageUpdateChannel,
      final Eth2P2PNetwork p2pNetwork,
      final BlockImporter blockImporter,
      final BlobSidecarManager blobSidecarManager,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final SignatureVerificationService signatureVerificationService) {
    return new DefaultSyncServiceFactory(
        syncConfig,
        eth2NetworkConfig.getNetworkBoostrapConfig().getGenesisState(),
        metricsSystem,
        asyncRunnerFactory,
        beaconAsyncRunner,
        timeProvider,
        recentChainData,
        combinedChainDataClient,
        storageUpdateChannel,
        p2pNetwork,
        blockImporter,
        blobSidecarManager,
        pendingBlocksPool,
        blockBlobSidecarsTrackersPool,
        eth2NetworkConfig.getStartupTargetPeerCount(),
        signatureVerificationService,
        Duration.ofSeconds(eth2NetworkConfig.getStartupTimeoutSeconds()),
        spec);
  }

  @Provides
  @Singleton
  static SyncService syncService(
      final SyncServiceFactory syncServiceFactory,
      final EventChannels eventChannels,
      final ForkChoice forkChoice,
      final ForkChoiceNotifier forkChoiceNotifier,
      final DepositProvider depositProvider,
      final Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      final Eth2P2PNetwork p2pNetwork,
      final ChainHeadChannel chainHeadChannelPublisher,
      final EventLogger eventLogger) {
    SyncService syncService = syncServiceFactory.create(eventChannels);

    // chainHeadChannel subscription
    CoalescingChainHeadChannel coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(chainHeadChannelPublisher, eventLogger);
    syncService.getForwardSync().subscribeToSyncChanges(coalescingChainHeadChannel);

    // forkChoiceNotifier subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> forkChoiceNotifier.onSyncingStatusChanged(syncState.isInSync()));

    // depositProvider subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> depositProvider.onSyncingStatusChanged(syncState.isInSync()));

    // forkChoice subscription
    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(syncService.getOptimisticSyncSubscriber());

    // terminalPowBlockMonitor subscription
    terminalPowBlockMonitor.ifPresent(
        monitor ->
            syncService.subscribeToSyncStateChangesAndUpdate(
                syncState -> monitor.onNodeSyncStateChanged(syncState.isInSync())));

    // p2pNetwork subscription so gossip can be enabled and disabled appropriately
    syncService.subscribeToSyncStateChangesAndUpdate(
        state -> p2pNetwork.onSyncStateChanged(state.isInSync(), state.isOptimistic()));

    return syncService;
  }

  @Provides
  @Singleton
  static RecentBlocksFetcher recentBlocksFetcher(
      final SyncService syncService,
      final BlockManager blockManager,
      final EventChannelSubscriber<ReceivedBlockEventsChannel> receivedBlockEventsChannelSubscriber,
      final LoggingModule.InitLogger initLogger) {
    final RecentBlocksFetcher recentBlocksFetcher = syncService.getRecentBlocksFetcher();
    recentBlocksFetcher.subscribeBlockFetched(
        (block) ->
            blockManager
                .importBlock(
                    block,
                    BroadcastValidationLevel.NOT_REQUIRED,
                    Optional.of(BlobSidecarManager.RemoteOrigin.RPC))
                .thenCompose(
                    BlockImportChannel.BlockImportAndBroadcastValidationResults::blockImportResult)
                .finish(
                    err ->
                        initLogger
                            .logger()
                            .error("Failed to process recently fetched block.", err)));
    receivedBlockEventsChannelSubscriber.subscribe(recentBlocksFetcher);
    return recentBlocksFetcher;
  }

  @Provides
  @Singleton
  static RecentBlobSidecarsFetcher recentBlobSidecarsFetcher(
      final SyncService syncService, final BlobSidecarManager blobSidecarManager) {
    final RecentBlobSidecarsFetcher recentBlobSidecarsFetcher =
        syncService.getRecentBlobSidecarsFetcher();
    recentBlobSidecarsFetcher.subscribeBlobSidecarFetched(
        (blobSidecar) ->
            blobSidecarManager.prepareForBlockImport(
                blobSidecar, BlobSidecarManager.RemoteOrigin.RPC));
    blobSidecarManager.subscribeToReceivedBlobSidecar(
        blobSidecar ->
            recentBlobSidecarsFetcher.cancelRecentBlobSidecarRequest(
                new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex())));
    return recentBlobSidecarsFetcher;
  }
}
