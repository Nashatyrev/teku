package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface BlockBlobChecker {

  static BlockBlobChecker createFromCanonicalBlockResolver(
      CanonicalBlockResolver canonicalBlockResolver) {
    return blockId -> canonicalBlockResolver
        .getBlockAtSlot(blockId.getSlot())
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .filter(block -> block.getRoot().equals(blockId.getBlockRoot()))
                    .flatMap(BeaconBlock::getBeaconBlock)
                    .flatMap(b -> b.getBody().toVersionEip7594())
                    .map(b -> !b.getBlobKzgCommitments().isEmpty())
                    .orElse(false));
  }

  SafeFuture<Boolean> hasBlobs(SlotAndBlockRoot blockId);
}
