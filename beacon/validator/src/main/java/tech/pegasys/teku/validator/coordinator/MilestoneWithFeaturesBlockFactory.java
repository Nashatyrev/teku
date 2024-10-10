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

package tech.pegasys.teku.validator.coordinator;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFeature;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.features.Eip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class MilestoneWithFeaturesBlockFactory implements BlockFactory {

  private final TreeMap<UInt64, BlockFactory> registeredFactories = new TreeMap<>();

  private final Spec spec;

  public MilestoneWithFeaturesBlockFactory(
      final Spec spec, final BlockOperationSelectorFactory operationSelector, final KZG kzg) {
    this.spec = spec;
    final BlockFactoryPhase0 blockFactoryPhase0 = new BlockFactoryPhase0(spec, operationSelector);

    // Not needed for all milestones
    final Supplier<BlockFactoryDeneb> blockFactoryDenebSupplier =
        Suppliers.memoize(() -> new BlockFactoryDeneb(spec, operationSelector));
    final Supplier<BlockFactoryEip7594> blockFactoryEip7594Supplier =
        Suppliers.memoize(() -> new BlockFactoryEip7594(spec, operationSelector, kzg));

    // Populate forks factories
    registeredFactories.put(UInt64.ZERO, blockFactoryPhase0);
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      registeredFactories.put(
          spec.getForkSchedule().getFork(SpecMilestone.DENEB).getEpoch(),
          blockFactoryDenebSupplier.get());
    }
    if (spec.isFeatureScheduled(SpecFeature.EIP7594)) {
      registeredFactories.put(
          // TODO: don't like to pull Electra for this
          Eip7594.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig())
              .getEip7594FeatureEpoch(),
          blockFactoryEip7594Supplier.get());
    }
  }

  // TODO: test
  private BlockFactory getFactoryForSlot(final UInt64 slot) {
    return registeredFactories.headMap(spec.computeEpochAtSlot(slot), true).lastEntry().getValue();
  }

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BeaconState blockSlotState,
      final UInt64 proposalSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    return getFactoryForSlot(proposalSlot)
        .createUnsignedBlock(
            blockSlotState,
            proposalSlot,
            randaoReveal,
            optionalGraffiti,
            requestedBuilderBoostFactor,
            blockProductionPerformance);
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return getFactoryForSlot(maybeBlindedBlock.getSlot())
        .unblindSignedBlockIfBlinded(maybeBlindedBlock, blockPublishingPerformance);
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(
      final SignedBlockContainer blockContainer,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return getFactoryForSlot(blockContainer.getSlot())
        .createBlobSidecars(blockContainer, blockPublishingPerformance);
  }

  @Override
  public List<DataColumnSidecar> createDataColumnSidecars(
      final SignedBlockContainer blockContainer, List<Blob> blobs) {
    return getFactoryForSlot(blockContainer.getSlot())
        .createDataColumnSidecars(blockContainer, blobs);
  }
}
