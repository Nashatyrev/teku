package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

import java.util.Optional;

public interface CanonicalBlockResolver {

  /**
   * Should return the canonical block root at slot if: - a block exist at this slot - block
   * contains any blobs
   */
  Optional<BeaconBlock> getBlockAtSlot(UInt64 slot);
}
