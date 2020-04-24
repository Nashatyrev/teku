package tech.pegasys.artemis.core.blockvalidator;

import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.async.SafeFuture;

public class AsyncValidator implements BlockValidator {

  private final BlockValidator synchronousValidator;

  public AsyncValidator(BlockValidator synchronousValidator) {
    this.synchronousValidator = synchronousValidator;
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePreState(
      BeaconState preState,
      SignedBeaconBlock block) {
    return SafeFuture.supplyAsync(() -> synchronousValidator.validatePreState(preState, block).join());
 }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(
      BeaconState postState,
      SignedBeaconBlock block) {
    return synchronousValidator.validatePostState(postState, block);
  }
}
