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

package tech.pegasys.teku.validator.coordinator.publisher;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFeature;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.features.Eip7594;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class MilestoneAndFeatureBasedBlockPublisher implements BlockPublisher {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final NavigableMap<UInt64, BlockPublisher> registeredPublishers = new TreeMap<>();

  public MilestoneAndFeatureBasedBlockPublisher(
      final AsyncRunner asyncRunner,
      final Spec spec,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final BlobSidecarGossipChannel blobSidecarGossipChannel,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final DutyMetrics dutyMetrics,
      final boolean gossipBlobsAfterBlock) {
    this.spec = spec;
    final BlockPublisherPhase0 blockPublisherPhase0 =
        new BlockPublisherPhase0(
            asyncRunner,
            blockFactory,
            blockGossipChannel,
            blockImportChannel,
            dutyMetrics,
            gossipBlobsAfterBlock);

    // Not needed for all milestones
    final Supplier<BlockPublisherDeneb> blockAndBlobSidecarsPublisherSupplier =
        Suppliers.memoize(
            () ->
                new BlockPublisherDeneb(
                    asyncRunner,
                    blockFactory,
                    blockImportChannel,
                    blockGossipChannel,
                    blockBlobSidecarsTrackersPool,
                    blobSidecarGossipChannel,
                    dutyMetrics,
                    gossipBlobsAfterBlock));
    final Supplier<BlockPublisherEip7594> blockAndDataColumnSidecarsPublisherSupplier =
        Suppliers.memoize(
            () ->
                new BlockPublisherEip7594(
                    asyncRunner,
                    blockFactory,
                    blockImportChannel,
                    blockGossipChannel,
                    dataColumnSidecarGossipChannel,
                    dutyMetrics,
                    gossipBlobsAfterBlock));

    // Populate forks publishers
    spec.getEnabledMilestones()
        .forEach(
            forkAndSpecMilestone -> {
              final SpecMilestone milestone = forkAndSpecMilestone.getSpecMilestone();
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                registeredPublishers.put(
                    forkAndSpecMilestone.getFork().getEpoch(),
                    blockAndBlobSidecarsPublisherSupplier.get());
              } else {
                registeredPublishers.put(
                    forkAndSpecMilestone.getFork().getEpoch(), blockPublisherPhase0);
              }
            });
    if (spec.isFeatureScheduled(SpecFeature.EIP7594)) {
      // TODO: make helper for this
      final UInt64 activationEpoch =
          Eip7594.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig())
              .getEip7594FeatureEpoch();
      final List<UInt64> publishersToRemove =
          registeredPublishers.tailMap(activationEpoch).keySet().stream().toList();
      publishersToRemove.forEach(registeredPublishers::remove);
      registeredPublishers.put(activationEpoch, blockAndDataColumnSidecarsPublisherSupplier.get());
    }
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    final BlockPublisher blockPublisher =
        registeredPublishers
            .floorEntry(spec.computeEpochAtSlot(blockContainer.getSlot()))
            .getValue();
    LOG.info(
        "Using {} block publisher for {}",
        blockPublisher,
        blockContainer.getSignedBlock().getSlotAndBlockRoot());
    return blockPublisher.sendSignedBlock(
        blockContainer, broadcastValidationLevel, blockPublishingPerformance);
  }
}
