/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

public class ForkChoiceUtil {

  protected final SpecConfig specConfig;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final EpochProcessor epochProcessor;
  protected final AttestationUtil attestationUtil;
  protected final MiscHelpers miscHelpers;

  public ForkChoiceUtil(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.epochProcessor = epochProcessor;
    this.attestationUtil = attestationUtil;
    this.miscHelpers = miscHelpers;
  }

  private UInt64 getCurrentSlot(final ReadOnlyStore store) {
    return miscHelpers.computeSlotAtTime(store.getGenesisTime(), store.getTimeSeconds());
  }

  private UInt64 computeSlotsSinceEpochStart(final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final UInt64 epochStartSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return slot.minus(epochStartSlot);
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public Optional<Bytes32> getAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 root, final UInt64 slot) {
    return forkChoiceStrategy.getAncestor(root, slot);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot,
      final UInt64 step,
      final UInt64 count) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    // minus(ONE) because the start block is included
    final UInt64 endSlot = startSlot.plus(step.times(count)).minusMinZero(1);
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().compareTo(startSlot) > 0) {
      maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
    return roots;
  }

  /**
   * @param forkChoiceStrategy the object that stores information on forks and block roots
   * @param root the root that dictates the block/fork that we walk backwards from
   * @param startSlot the slot (exclusive) until which we walk the chain backwards
   * @return every block root from root (inclusive) to start slot (exclusive) traversing the chain
   *     backwards
   */
  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().isGreaterThan(startSlot)) {
      maybeAddRoot(startSlot, UInt64.ONE, roots, UInt64.MAX_VALUE, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    return roots;
  }

  /**
   * is_shuffling_stable
   *
   * <p>Refer to fork-choice specification.
   *
   * @param slot
   * @return
   */
  public boolean isShufflingStable(final UInt64 slot) {
    return !slot.mod(specConfig.getSlotsPerEpoch()).isZero();
  }

  public boolean isFinalizationOk(final ReadOnlyStore store, final UInt64 slot) {
    final UInt64 epochsSinceFinalization =
        miscHelpers
            .computeEpochAtSlot(slot)
            .minusMinZero(store.getFinalizedCheckpoint().getEpoch());
    return epochsSinceFinalization.isLessThanOrEqualTo(
        specConfig.getReorgMaxEpochsSinceFinalization());
  }

  private void maybeAddRoot(
      final UInt64 startSlot,
      final UInt64 step,
      final NavigableMap<UInt64, Bytes32> roots,
      final UInt64 endSlot,
      final Bytes32 root,
      final Optional<UInt64> maybeSlot) {
    maybeSlot.ifPresent(
        slot -> {
          if (slot.compareTo(endSlot) <= 0
              && slot.compareTo(startSlot) >= 0
              && slot.minus(startSlot).mod(step).equals(UInt64.ZERO)) {
            roots.put(slot, root);
          }
        });
  }

  // Fork Choice Event Handlers

  /**
   * The implementation differs from the spec in that the time parameter is in milliseconds instead
   * of seconds. This allows the time to be stored in milliseconds which allows for more
   * fine-grained time calculations if required.
   *
   * @param store the store to update
   * @param timeMillis onTick time in milliseconds
   */
  public void onTick(final MutableStore store, final UInt64 timeMillis) {
    // To be extra safe check both time and genesisTime, although time should always be >=
    // genesisTime
    if (store.getTimeInMillis().isGreaterThan(timeMillis)
        || store.getGenesisTimeMillis().isGreaterThan(timeMillis)) {
      return;
    }
    final UInt64 previousSlot = getCurrentSlot(store);
    final UInt64 newSlot =
        miscHelpers.computeSlotAtTimeMillis(store.getGenesisTimeMillis(), timeMillis);
    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    final UInt64 newEpoch = miscHelpers.computeEpochAtSlot(newSlot);

    // Catch up on any missed epoch transitions
    for (UInt64 currentEpoch = previousEpoch.plus(1);
        currentEpoch.isLessThanOrEqualTo(newEpoch);
        currentEpoch = currentEpoch.plus(1)) {
      final UInt64 firstSlotOfEpoch = miscHelpers.computeStartSlotAtEpoch(currentEpoch);
      final UInt64 slotStartTime =
          miscHelpers.computeTimeMillisAtSlot(store.getGenesisTimeMillis(), firstSlotOfEpoch);
      processOnTick(store, slotStartTime);
    }
    // Catch up final part
    processOnTick(store, timeMillis);
  }

  private void processOnTick(final MutableStore store, final UInt64 timeMillis) {
    final UInt64 previousSlot = getCurrentSlot(store);

    // Update store time
    store.setTimeMillis(timeMillis);

    final UInt64 currentSlot = getCurrentSlot(store);

    if (currentSlot.isGreaterThan(previousSlot)) {
      store.removeProposerBoostRoot();
    }

    // Not a new epoch, return
    if (!(currentSlot.isGreaterThan(previousSlot)
        && computeSlotsSinceEpochStart(currentSlot).isZero())) {
      return;
    }

    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    for (ProtoNodeData nodeData : store.getForkChoiceStrategy().getChainHeads(true)) {
      if (miscHelpers.computeEpochAtSlot(nodeData.getSlot()).equals(previousEpoch)) {
        store.pullUpBlockCheckpoints(nodeData.getRoot());
        updateCheckpoints(
            store,
            nodeData.getCheckpoints().getUnrealizedJustifiedCheckpoint(),
            nodeData.getCheckpoints().getUnrealizedFinalizedCheckpoint(),
            nodeData.isOptimistic());
      }
    }
  }

  private void updateCheckpoints(
      final MutableStore store,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final boolean isBlockOptimistic) {
    if (justifiedCheckpoint.getEpoch().isGreaterThan(store.getJustifiedCheckpoint().getEpoch())) {
      store.setJustifiedCheckpoint(justifiedCheckpoint);
    }
    if (finalizedCheckpoint.getEpoch().isGreaterThan(store.getFinalizedCheckpoint().getEpoch())) {
      store.setFinalizedCheckpoint(finalizedCheckpoint, isBlockOptimistic);
    }
  }

  @CheckReturnValue
  public AttestationProcessingResult validate(
      final Fork fork,
      final ReadOnlyStore store,
      final ValidatableAttestation validatableAttestation,
      final Optional<BeaconState> maybeState) {
    Attestation attestation = validatableAttestation.getAttestation();
    return validateOnAttestation(store, attestation.getData())
        .ifSuccessful(
            () -> {
              if (maybeState.isEmpty()) {
                return AttestationProcessingResult.UNKNOWN_BLOCK;
              } else {
                return attestationUtil.isValidIndexedAttestation(
                    fork, maybeState.get(), validatableAttestation);
              }
            })
        .ifSuccessful(() -> checkIfAttestationShouldBeSavedForFuture(store, attestation));
  }

  private AttestationProcessingResult validateOnAttestation(
      final ReadOnlyStore store, final AttestationData attestationData) {

    UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(getCurrentSlot(store));
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();

    return validateOnAttestation(forkChoiceStrategy, currentEpoch, attestationData);
  }

  public AttestationProcessingResult validateOnAttestation(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final AttestationData attestationData) {
    final Checkpoint target = attestationData.getTarget();

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    final UInt64 previousEpoch =
        currentEpoch.isGreaterThan(SpecConfig.GENESIS_EPOCH)
            ? currentEpoch.minus(UInt64.ONE)
            : SpecConfig.GENESIS_EPOCH;

    if (!target.getEpoch().equals(previousEpoch) && !target.getEpoch().equals(currentEpoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(miscHelpers.computeEpochAtSlot(attestationData.getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    final Optional<UInt64> blockSlot =
        forkChoiceStrategy.blockSlot(attestationData.getBeaconBlockRoot());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestationData.getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // LMD vote must be consistent with FFG vote target
    final UInt64 targetSlot = miscHelpers.computeStartSlotAtEpoch(target.getEpoch());
    if (getAncestor(forkChoiceStrategy, attestationData.getBeaconBlockRoot(), targetSlot)
        .map(ancestorRoot -> !ancestorRoot.equals(target.getRoot()))
        .orElse(true)) {
      return AttestationProcessingResult.invalid(
          "LMD vote must be consistent with FFG vote target");
    }

    return AttestationProcessingResult.SUCCESSFUL;
  }

  private AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      final ReadOnlyStore store, final Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    final UInt64 currentSlot = getCurrentSlot(store);
    if (currentSlot.isLessThan(attestation.getData().getSlot())) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    if (currentSlot.equals(attestation.getData().getSlot())) {
      return AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    final UInt64 epochStartSlot =
        miscHelpers.computeStartSlotAtEpoch(attestation.getData().getTarget().getEpoch());
    if (currentSlot.isLessThan(epochStartSlot)) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  /**
   * Stores block and associated blobSidecars if any into the store
   *
   * @param store Store to apply block to
   * @param signedBlock Block
   * @param postState State after applying this block
   * @param isBlockOptimistic optimistic/non-optimistic
   * @param blobSidecars BlobSidecars
   * @param earliestBlobSidecarsSlot not required even post-Deneb, saved only if the new value is
   *     smaller
   */
  public void applyBlockToStore(
      final MutableStore store,
      final SignedBeaconBlock signedBlock,
      final BeaconState postState,
      final boolean isBlockOptimistic,
      final Optional<List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> earliestBlobSidecarsSlot) {

    BlockCheckpoints blockCheckpoints = epochProcessor.calculateBlockCheckpoints(postState);

    // If block is from a prior epoch, pull up the post-state to next epoch to realize new finality
    // info
    if (miscHelpers
        .computeEpochAtSlot(signedBlock.getSlot())
        .isLessThan(miscHelpers.computeEpochAtSlot(getCurrentSlot(store)))) {
      blockCheckpoints = blockCheckpoints.realizeNextEpoch();
    }

    updateCheckpoints(
        store,
        blockCheckpoints.getJustifiedCheckpoint(),
        blockCheckpoints.getFinalizedCheckpoint(),
        isBlockOptimistic);

    // Add new block to store
    store.putBlockAndState(
        signedBlock, postState, blockCheckpoints, blobSidecars, earliestBlobSidecarsSlot);
  }

  private UInt64 getFinalizedCheckpointStartSlot(final ReadOnlyStore store) {
    final UInt64 finalizedEpoch = store.getFinalizedCheckpoint().getEpoch();
    return miscHelpers.computeStartSlotAtEpoch(finalizedEpoch);
  }

  public BlockImportResult checkOnBlockConditions(
      final SignedBeaconBlock block, final BeaconState blockSlotState, final ReadOnlyStore store) {
    final UInt64 blockSlot = block.getSlot();
    if (blockSlotState == null) {
      return BlockImportResult.FAILED_UNKNOWN_PARENT;
    }
    if (!blockSlotState.getSlot().equals(blockSlot)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockSlot.isGreaterThan(SpecConfig.GENESIS_SLOT)
        && !beaconStateAccessors
            .getBlockRootAtSlot(blockSlotState, blockSlot.minus(1))
            .equals(block.getParentRoot())) {
      // Block is at same slot as its parent or the parent root doesn't match the state
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE;
    }
    if (!blockDescendsFromLatestFinalizedBlock(
        block.getSlot(), block.getParentRoot(), store, store.getForkChoiceStrategy())) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    // Successful so far
    return BlockImportResult.successful(block);
  }

  private boolean blockIsFromFuture(final ReadOnlyStore store, final UInt64 blockSlot) {
    return getCurrentSlot(store).compareTo(blockSlot) < 0;
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final UInt64 blockSlot,
      final Bytes32 parentBlockRoot,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();

    // Make sure this block's slot is after the latest finalized slot
    final UInt64 finalizedEpochStartSlot = getFinalizedCheckpointStartSlot(store);
    return blockIsAfterLatestFinalizedSlot(blockSlot, finalizedEpochStartSlot)
        && hasAncestorAtSlot(
            forkChoiceStrategy,
            parentBlockRoot,
            finalizedEpochStartSlot,
            finalizedCheckpoint.getRoot());
  }

  private boolean blockIsAfterLatestFinalizedSlot(
      final UInt64 blockSlot, final UInt64 finalizedEpochStartSlot) {
    return blockSlot.compareTo(finalizedEpochStartSlot) > 0;
  }

  private boolean hasAncestorAtSlot(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 slot,
      final Bytes32 ancestorRoot) {
    return getAncestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }

  public boolean canOptimisticallyImport(final ReadOnlyStore store, final SignedBeaconBlock block) {
    // A block can be optimistically imported either if it's parent contains a non-default payload
    // or if it is at least `SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY` before the current slot.
    // This is to avoid the merge block referencing a non-existent eth1 parent block and causing
    // all nodes to switch to optimistic sync mode.
    if (isExecutionBlock(store, block)) {
      return true;
    }
    return isBellatrixBlockOld(store, block.getSlot());
  }

  /** non-functional in early forks */
  public Optional<UInt64> getEarliestAvailabilityWindowSlotBeforeBlock(
      final Spec spec, final ReadOnlyStore store, final UInt64 slot) {
    return Optional.empty();
  }

  private boolean isBellatrixBlockOld(final ReadOnlyStore store, final UInt64 blockSlot) {
    final Optional<SpecConfigBellatrix> maybeConfig = specConfig.toVersionBellatrix();
    if (maybeConfig.isEmpty()) {
      return false;
    }
    return blockSlot
        .plus(maybeConfig.get().getSafeSlotsToImportOptimistically())
        .isLessThanOrEqualTo(getCurrentSlot(store));
  }

  private boolean isExecutionBlock(final ReadOnlyStore store, final SignedBeaconBlock block) {
    // post-Bellatrix: always true
    final BeaconBlockBody body = block.getMessage().getBody();
    if (body.toVersionCapella().isPresent() || body.toBlindedVersionCapella().isPresent()) {
      return true;
    }
    final Optional<Bytes32> parentExecutionRoot =
        store.getForkChoiceStrategy().executionBlockHash(block.getParentRoot());
    return parentExecutionRoot.isPresent() && !parentExecutionRoot.get().isZero();
  }

  // TODO extract to config
  private static final int COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR = 5;
  private static final int CONFIRMATION_BYZANTINE_THRESHOLD = 33;

  /**
   * Returns the total weight of committees between ``start_slot`` and ``end_slot`` (inclusive of
   * both). FIXME: spec: name function estimate* instead of get*
   */
  public UInt64 getCommitteeWeightBetweenSlots(
      BeaconState state, UInt64 startSlot, UInt64 endSlot) {

    //    total_active_balance = get_total_active_balance(state)
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(state);

    //    start_epoch = compute_epoch_at_slot(start_slot)
    //    end_epoch = compute_epoch_at_slot(end_slot)
    UInt64 startEpoch = miscHelpers.computeEpochAtSlot(startSlot);
    UInt64 endEpoch = miscHelpers.computeEpochAtSlot(endSlot);

    //    if start_slot > end_slot:
    //        return Gwei(0)
    if (startSlot.isGreaterThan(endSlot)) {
      return UInt64.ZERO;
    }

    UInt64 slotsPerEpoch = UInt64.valueOf(specConfig.getSlotsPerEpoch());
    //         end_epoch > start_epoch + 1
    //        or (end_epoch == start_epoch + 1 and start_slot % SLOTS_PER_EPOCH == 0))
    boolean ifFullValidatorSetCovered =
        endEpoch.isGreaterThan(startEpoch.increment())
            || (endEpoch.equals(startEpoch.increment()) && startSlot.mod(slotsPerEpoch).isZero());

    //    # If an entire epoch is covered by the range, return the total active balance
    //    if is_full_validator_set_covered(start_slot, end_slot):
    //        return total_active_balance
    if (ifFullValidatorSetCovered) {
      return totalActiveBalance;
    }

    //    if start_epoch == end_epoch:
    //        return total_active_balance // SLOTS_PER_EPOCH * (end_slot - start_slot + 1)
    if (startEpoch.equals(endEpoch)) {
      return totalActiveBalance
          .dividedBy(slotsPerEpoch)
          .times(endSlot.minus(startSlot).increment());
    }

    //    else:
    //        # A range that spans an epoch boundary, but does not span any full epoch
    //        # needs pro-rata calculation
    //
    //        # See https://gist.github.com/saltiniroberto/9ee53d29c33878d79417abb2b4468c20
    //        # for an explanation of the formula used below.
    //
    //        # First, calculate the number of committees in the end epoch
    //        num_slots_in_end_epoch = compute_slots_since_epoch_start(end_slot)
    UInt64 numSlotsInEndEpoch = computeSlotsSinceEpochStart(endSlot);
    //        # Next, calculate the number of slots remaining in the end epoch
    //        remaining_slots_in_end_epoch = SLOTS_PER_EPOCH - num_slots_in_end_epoch
    UInt64 remainingSlotsInEndEpoch = slotsPerEpoch.minus(numSlotsInEndEpoch);
    //        # Then, calculate the number of slots in the start epoch
    //        num_slots_in_start_epoch = SLOTS_PER_EPOCH -
    // compute_slots_since_epoch_start(start_slot)
    UInt64 numSlotsInStartEpoch = slotsPerEpoch.minus(computeSlotsSinceEpochStart(startSlot));
    //        end_epoch_weight_estimate = total_active_balance // SLOTS_PER_EPOCH *
    // num_slots_in_end_epoch
    UInt64 endEpochWeightEstimate =
        totalActiveBalance.dividedBy(slotsPerEpoch).times(numSlotsInEndEpoch);
    //        start_epoch_weight_estimate = (total_active_balance // SLOTS_PER_EPOCH //
    // SLOTS_PER_EPOCH *
    //            num_slots_in_start_epoch * remaining_slots_in_end_epoch)
    // FIXME: spec
    //        start_epoch_weight_estimate = (total_active_balance // SLOTS_PER_EPOCH *
    //            num_slots_in_start_epoch // SLOTS_PER_EPOCH * remaining_slots_in_end_epoch)
    UInt64 startEpochWeightEstimate =
        totalActiveBalance
            .dividedBy(slotsPerEpoch)
            .times(numSlotsInStartEpoch)
            .dividedBy(slotsPerEpoch)
            .times(remainingSlotsInEndEpoch);

    //        # Each committee from the end epoch only contributes a pro-rated weight
    //        return adjust_committee_weight_estimate_to_ensure_safety(
    //            Gwei(start_epoch_weight_estimate + end_epoch_weight_estimate)
    //        )
    UInt64 estimate = startEpochWeightEstimate.plus(endEpochWeightEstimate);
    //     Adjusts the ``estimate`` of the weight of a committee for a sequence of slots not
    // covering a full epoch to
    //    ensure the safety of the confirmation rule with high probability.
    //
    //    See https://gist.github.com/saltiniroberto/9ee53d29c33878d79417abb2b4468c20 for an
    // explanation of why this is
    //    required.
    //    """
    //    return Gwei(estimate // 1000 * (1000 + COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR))
    return estimate.dividedBy(1000).times(1000 + COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR);
  }

  // def is_one_confirmed(store: Store, block_root: Root) -> bool:
  boolean isOneConfirmed(
      final ReadOnlyStore store,
      final Bytes32 blockRoot,
      final BeaconState weightingCheckpointState) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    //    current_slot = get_current_slot(store)
    //    block = store.blocks[block_root]
    //    parent_block = store.blocks[block.parent_root]
    Bytes32 parentBlockRoot = forkChoiceStrategy.blockParentRoot(blockRoot).orElseThrow();
    UInt64 parentBlockSlot = forkChoiceStrategy.blockSlot(parentBlockRoot).orElseThrow();

    //
    //    if (miscHelpers.isFirstSlotInEpoch(currentSlot)) {
    // FIXME: (spec)
    //    if current_slot % SLOTS_PER_EPOCH == 0:
    //
    //        weighting_checkpoint = store.prev_slot_unrealized_justified_checkpoint
    //    else:
    //        weighting_checkpoint = store.prev_slot_justified_checkpoint
    //    weighting_checkpoint_state = store.checkpoint_states[weighting_checkpoint]

    //    support = get_weight(store, block_root, weighting_checkpoint_state)
    UInt64 support =
        forkChoiceStrategy.getWeight(blockRoot, weightingCheckpointState).orElseThrow();
    //    maximum_support = get_committee_weight_between_slots(
    //        weighting_checkpoint_state, Slot(parent_block.slot + 1), Slot(current_slot - 1))
    UInt64 maximumSupport =
        getCommitteeWeightBetweenSlots(
            weightingCheckpointState, parentBlockSlot.increment(), getCurrentSlot(store));
    //    proposer_score = get_proposer_score(store)
    // FIXME: here we deviate from spec. get_proposer_score() uses store.justified_checkpoint state
    UInt64 proposerScore = beaconStateAccessors.getProposerBoostAmount(weightingCheckpointState);
    //
    //    # Returns whether the following condition is true using only integer arithmetic
    //    # support / maximum_support >
    //    # 0.5 * (1 + proposer_score / maximum_support) + CONFIRMATION_BYZANTINE_THRESHOLD / 100
    //
    //    # 2 * support > maximum_support * (1 + 2 * CONFIRMATION_BYZANTINE_THRESHOLD / 100) +
    // proposer_score
    //    return (
    //        2 * support >
    //        maximum_support + maximum_support // 50 * CONFIRMATION_BYZANTINE_THRESHOLD +
    // proposer_score
    //    )
    return support
        .times(2)
        .isGreaterThan(
            maximumSupport
                .plus(maximumSupport.dividedBy(50).times(CONFIRMATION_BYZANTINE_THRESHOLD))
                .plus(proposerScore));
  }

  /** Compute the checkpoint block for epoch ``epoch`` in the chain of block ``root`` */
  Bytes32 getCheckpointBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    UInt64 epochFirstSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return store.getForkChoiceStrategy().getAncestor(root, epochFirstSlot).orElseThrow();
  }

  Checkpoint getCheckpointForBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    // FIXME spec deviation (looks equivalent)
    return new Checkpoint(epoch, getCheckpointBlock(store, root, epoch));
  }

  /** Uses LMD-GHOST votes to estimate FFG support for a checkpoint. */
  // def get_checkpoint_weight(store: Store, checkpoint: checkpoint_state, checkpoint_state:
  // BeaconState) -> Gwei:
  // FIXME (spec) fix checkpoint type
  UInt64 getCheckpointWeight(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {
    // TODO likely deep Protoarray modification is needed
    throw new UnsupportedOperationException("TODO");
  }

  // def get_ffg_weight_till_slot(slot: Slot, epoch: Epoch, total_active_balance: Gwei) -> Gwei:
  UInt64 getFfgWeightTillSlot(UInt64 slot, UInt64 epoch, UInt64 totalActiveBalance) {
    //    if slot <= compute_start_slot_at_epoch(epoch):
    //        return Gwei(0)
    if (slot.isLessThanOrEqualTo(miscHelpers.computeStartSlotAtEpoch(epoch))) {
      return UInt64.ZERO;
    }
    //    elif slot >= compute_start_slot_at_epoch(epoch + 1):
    //        return total_active_balance
    if (slot.isGreaterThanOrEqualTo(miscHelpers.computeStartSlotAtEpoch(epoch.increment()))) {
      return totalActiveBalance;
    }
    //    else:
    //        slots_passed = slot % SLOTS_PER_EPOCH
    //        return total_active_balance // SLOTS_PER_EPOCH * slots_passed
    UInt64 slotsPassed = slot.mod(specConfig.getSlotsPerEpoch());
    return totalActiveBalance.dividedBy(specConfig.getSlotsPerEpoch()).times(slotsPassed);
  }

  // def will_current_epoch_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  boolean willCurrentEpochCheckpointBeJustified(ReadOnlyStore store, Checkpoint checkpoint) {
    // TODO
    return true;
  }

  //    assert checkpoint.epoch == get_current_epoch_store(store)
  //
  //    current_slot = get_current_slot(store)
  //    current_epoch = compute_epoch_at_slot(current_slot)
  //
  //    store_target_checkpoint_state(store, checkpoint)
  //    checkpoint_state = store.checkpoint_states[checkpoint]
  //
  //    total_active_balance = get_total_active_balance(checkpoint_state)
  //
  //    # compute FFG support for checkpoint
  //    ffg_support_for_checkpoint = get_checkpoint_weight(store, checkpoint, checkpoint_state)
  //
  //    # compute total FFG weight till current slot
  //    ffg_weight_till_now = get_ffg_weight_till_slot(current_slot, current_epoch,
  // total_active_balance)
  //
  //    # compute remaining honest FFG weight
  //    remaining_ffg_weight = total_active_balance - ffg_weight_till_now
  //    remaining_honest_ffg_weight = Gwei(remaining_ffg_weight // 100 * (100 -
  // config.CONFIRMATION_BYZANTINE_THRESHOLD))
  //
  //    # compute min honest FFG support
  //    min_honest_ffg_support = ffg_support_for_checkpoint - min(
  //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_BYZANTINE_THRESHOLD),
  //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_SLASHING_THRESHOLD),
  //        ffg_support_for_checkpoint
  //    )
  //
  //    return 3 * (min_honest_ffg_support + remaining_honest_ffg_weight) >= 2 *
  // total_active_balance
  //
  //
  // def will_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  boolean willCheckpointBeJustified(ReadOnlyStore store, Checkpoint checkpoint) {
    // TODO
    return true;
  }

  //    if checkpoint == store.justified_checkpoint:
  //        return True
  //
  //    if checkpoint == store.unrealized_justified_checkpoint:
  //        return True
  //
  //    if checkpoint.epoch == get_current_epoch_store(store):
  //        return will_current_epoch_checkpoint_be_justified(store, checkpoint)
  //
  //    return False
  //
  //
  // def will_no_conflicting_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  boolean willNoConflictingCheckpointBeJustified(ReadOnlyStore store, Checkpoint checkpoint) {
    // TODO
    return true;
  }

  //    assert checkpoint.epoch == get_current_epoch_store(store)
  //
  //    current_slot = get_current_slot(store)
  //    current_epoch = compute_epoch_at_slot(current_slot)
  //
  //    store_target_checkpoint_state(store, checkpoint)
  //    checkpoint_state = store.checkpoint_states[checkpoint]
  //
  //    total_active_balance = get_total_active_balance(checkpoint_state)
  //
  //    # compute FFG support for checkpoint
  //    ffg_support_for_checkpoint = get_checkpoint_weight(store, checkpoint, checkpoint_state)
  //
  //    # compute total FFG weight till current slot
  //    ffg_weight_till_now = get_ffg_weight_till_slot(current_slot, current_epoch,
  // total_active_balance)
  //
  //    # compute remaining honest FFG weight
  //    remaining_ffg_weight = total_active_balance - ffg_weight_till_now
  //    remaining_honest_ffg_weight = Gwei(remaining_ffg_weight // 100 * (100 -
  // config.CONFIRMATION_BYZANTINE_THRESHOLD))
  //
  //    # compute min honest FFG support
  //    min_honest_ffg_support = ffg_support_for_checkpoint - min(
  //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_BYZANTINE_THRESHOLD),
  //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_SLASHING_THRESHOLD),
  //        ffg_support_for_checkpoint
  //    )
  //
  //    return 3 * (min_honest_ffg_support + remaining_honest_ffg_weight) >= total_active_balance

  UInt64 getBlockEpoch(ReadOnlyStore store, Bytes32 blockRoot) {
    UInt64 blockSlot = store.getForkChoiceStrategy().blockSlot(blockRoot).orElseThrow();
    return miscHelpers.computeEpochAtSlot(blockSlot);
  }

  UInt64 getCurrentStoreEpoch(ReadOnlyStore store) {
    UInt64 currentSlot = getCurrentSlot(store);
    return miscHelpers.computeEpochAtSlot(currentSlot);
  }

  List<Bytes32> getChainRoots(ReadOnlyStore store, UInt64 ancestorSlot, Bytes32 startBlockRoot) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    ArrayList<Bytes32> ret = new ArrayList<>();
    Bytes32 root = startBlockRoot;
    ret.add(root);
    while (forkChoiceStrategy.blockSlot(root).orElseThrow().isGreaterThan(ancestorSlot)) {
      root = forkChoiceStrategy.blockParentRoot(root).orElseThrow();
      ret.add(root);
    }
    return ret.reversed();
  }

  /**
   * Compute the voting source checkpoint in event that block with root ``block_root`` is the head
   * block
   */
  // def get_voting_source(store: Store, block_root: Root) -> Checkpoint:
  Checkpoint getVotingSource(ReadOnlyStore store, Bytes32 blockRoot) {
    // block = store.blocks[block_root]
    // current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentStoreEpoch(store);
    // block_epoch = compute_epoch_at_slot(block.slot)
    UInt64 blockEpoch = getBlockEpoch(store, blockRoot);
    // if current_epoch > block_epoch:
    BlockCheckpoints blockCheckpoints =
        store.getForkChoiceStrategy().getBlockData(blockRoot).orElseThrow().getCheckpoints();
    if (currentEpoch.isGreaterThan(blockEpoch)) {
      // # The block is from a prior epoch, the voting source will be pulled-up
      // return store.unrealized_justifications[block_root]
      return blockCheckpoints.getUnrealizedJustifiedCheckpoint();
    } else {
      // else:
      //   # The block is not from a prior epoch, therefore the voting source is not pulled up
      //   head_state = store.block_states[block_root]
      //   return head_state.current_justified_checkpoint
      // FIXME: different from spec but looks equivalent
      return blockCheckpoints.getJustifiedCheckpoint();
    }
  }

  // def is_ancestor(store: Store, root: Root, ancestor: Root):
  //    assert root in store.blocks
  //    assert ancestor in store.blocks
  //
  //    return get_ancestor(store, root, store.block[ancestor].slot) == ancestor
  boolean isAncestor(ReadOnlyStore store, Bytes32 root, Bytes32 ancestor) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    UInt64 ancestorSlot = forkChoiceStrategy.blockSlot(ancestor).orElseThrow();
    return forkChoiceStrategy.getAncestor(root, ancestorSlot).orElseThrow().equals(ancestorSlot);
  }

  /**
   * This function assumes that the ``latest_confirmed_root`` belongs to the canonical chain and is
   * either from the previous or from the current epoch.
   */
  // def find_latest_confirmed_descendant(store: Store, latest_confirmed_root: Root) -> Root:
  Bytes32 findLatestConfirmedDescendant(
      ReadOnlyStore store,
      Bytes32 latestConfirmedRoot,
      Bytes32 headBlockRoot /* TODO probably worth adding it to Store */,
      BeaconState weightingCheckpointState) {
    // current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentStoreEpoch(store);
    // # verify the latest confirmed block is not too old
    // assert compute_block_epoch(latest_confirmed_root) + 1 >= current_epoch
    // FIXME (spec) no compute_block_epoch() function found
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    UInt64 confirmedSlot = forkChoiceStrategy.blockSlot(latestConfirmedRoot).orElseThrow();
    checkArgument(
        miscHelpers
            .computeEpochAtSlot(confirmedSlot)
            .increment()
            .isGreaterThanOrEqualTo(currentEpoch));
    // head = get_head(store)
    // confirmed_root = latest_confirmed_root
    Bytes32 confirmedRoot = latestConfirmedRoot;
    // if (get_block_epoch(store, confirmed_root) + 1 == current_epoch
    //     and get_voting_source(store, store.prev_slot_head).epoch + 2 >= current_epoch
    //     and (get_current_slot(store) % SLOTS_PER_EPOCH == 0
    //          or (will_no_conflicting_checkpoint_be_justified(store,
    // get_checkpoint_block(store, head, current_epoch))
    //               and (store.unrealized_justifications[store.prev_slot_head].epoch + 1 >=
    // current_epoch
    //                  or store.unrealized_justifications[head].epoch + 1 >= current_epoch)))):

    boolean isConfirmedBlockFromPreviousEpoch =
        getBlockEpoch(store, confirmedRoot).increment().equals(currentEpoch);
    Checkpoint prevHeadSourceCheckpoint = getVotingSource(store, store.getPrevSlotHead());
    boolean isPrevHeadSourceTooOld =
        prevHeadSourceCheckpoint.getEpoch().plus(2).isLessThan(currentEpoch);
    boolean isFirstEpochSlot = getCurrentSlot(store).mod(specConfig.getSlotsPerEpoch()).isZero();
    // FIXME sepc deviation with getCheckpointForBlock
    boolean willNoConflictingCheckpointBeJustified =
        willNoConflictingCheckpointBeJustified(
            store, getCheckpointForBlock(store, headBlockRoot, currentEpoch));
    Checkpoint prevHeadUnrealizedJustifiedCheckpoint =
        forkChoiceStrategy
            .getBlockData(store.getPrevSlotHead())
            .orElseThrow()
            .getCheckpoints()
            .getUnrealizedJustifiedCheckpoint();
    boolean isPrevHeadUnrealizedJustifiedCheckpointOld =
        prevHeadUnrealizedJustifiedCheckpoint.getEpoch().increment().isLessThan(currentEpoch);
    Checkpoint currHeadUnrealizedJustifiedCheckpoint =
        forkChoiceStrategy
            .getBlockData(headBlockRoot)
            .orElseThrow()
            .getCheckpoints()
            .getUnrealizedJustifiedCheckpoint();
    boolean isCurrHeadUnrealizedJustifiedCheckpointOld =
        currHeadUnrealizedJustifiedCheckpoint.getEpoch().increment().isLessThan(currentEpoch);

    // FIXME (spec) needs deobfuscation and explanation
    if (isConfirmedBlockFromPreviousEpoch
        && !isPrevHeadSourceTooOld
        && (isFirstEpochSlot
            || (willNoConflictingCheckpointBeJustified
                && !(isPrevHeadUnrealizedJustifiedCheckpointOld
                    && isCurrHeadUnrealizedJustifiedCheckpointOld)))) {

      // # retrieve suffix of the canonical chain
      // # verify the latest_confirmed_root belongs to it
      // canonical_roots = get_canonical_roots(store, confirmed_root)
      // FIXME (spec) confirmed_root -> slot ?
      List<Bytes32> canonicalRoots = getChainRoots(store, confirmedSlot, headBlockRoot);
      // assert canonical_roots.pop(0) == confirmed_root
      checkArgument(canonicalRoots.get(0).equals(confirmedRoot));
      canonicalRoots.remove(0);
      // # starting with the child of the latest_confirmed_root
      // # move towards the head in attempt to advance confirmed block
      // # and stop when the first unconfirmed descendant is encountered for block_root in
      // canonical_roots:
      for (Bytes32 blockRoot : canonicalRoots) {
        // block_epoch = compute_epoch_at_slot(store.blocks[block_root].slot)
        // FIXME (spec): can replace with get_block_epoch()
        UInt64 blockEpoch = getBlockEpoch(store, blockRoot);
        // # If we reach the current epoch, we exit as this code is only for confirming blocks from
        // the previous epoch
        // if block_epoch == current_epoch:
        //     break
        if (blockEpoch.equals(currentEpoch)) {
          break;
        }
        // # We can only rely on the previous head if it is a descendant of the block we are
        // attempting to confirm
        // if not is_ancestor(store, store.prev_slot_head, block_root):
        //     break
        if (!isAncestor(store, store.getPrevSlotHead(), blockRoot)) {
          break;
        }
        // if not is_one_confirmed(store, block_root):
        //     break
        if (!isOneConfirmed(store, blockRoot, weightingCheckpointState)) {
          break;
        }
        // confirmed_root = block_root
        confirmedRoot = blockRoot;
      }
    }

    // if (get_current_slot(store) % SLOTS_PER_EPOCH == 0
    //    or store.unrealized_justifications[head].epoch + 1 >= current_epoch):
    if (isFirstEpochSlot || !isCurrHeadUnrealizedJustifiedCheckpointOld) {
      // # retrieve suffix of the canonical chain
      // # verify the latest_confirmed_root belongs to it
      // canonical_roots = get_canonical_roots(store, confirmed_root)
      // assert canonical_roots.pop(0) == confirmed_root
      UInt64 newConfirmedSlot = forkChoiceStrategy.blockSlot(confirmedRoot).orElseThrow();
      List<Bytes32> canonicalRoots = getChainRoots(store, newConfirmedSlot, headBlockRoot);
      // assert canonical_roots.pop(0) == confirmed_root
      checkArgument(canonicalRoots.get(0).equals(confirmedRoot));
      canonicalRoots.remove(0);
      // tentative_confirmed_root = confirmed_root
      Bytes32 tentativeConfirmedRoot = confirmedRoot;
      // for block_root in canonical_roots:
      //     block_epoch = compute_epoch_at_slot(store.blocks[block_root].slot)
      // FIXME (spec): can replace with get_block_epoch()
      for (Bytes32 blockRoot : canonicalRoots) {
        UInt64 blockEpoch = getBlockEpoch(store, blockRoot);

        // tentative_confirmed_epoch =
        //     compute_epoch_at_slot(store.blocks[tentative_confirmed_root].slot)
        UInt64 tentativeConfirmedEpoch = getBlockEpoch(store, tentativeConfirmedRoot);

        // # The following condition can only be true the first time that we advance to a
        // # block from the current epoch
        // if block_epoch > tentative_confirmed_epoch:
        if (blockEpoch.isGreaterThan(tentativeConfirmedEpoch)) {
          // checkpoint_root = get_checkpoint_block(store, block_root, block_epoch)
          // checkpoint = Checkpoint(checkpoint_root, block_epoch)
          Checkpoint checkpoint = getCheckpointForBlock(store, blockRoot, blockEpoch);
          // # To confirm blocks from the current epoch ensure that
          // # current epoch checkpoint will be justified
          // if not will_checkpoint_be_justified(store, checkpoint):
          //     break
          if (!willCheckpointBeJustified(store, checkpoint)) {
            break;
          }
        }
        // if not is_one_confirmed(store, block_root):
        //    break
        if (!isOneConfirmed(store, blockRoot, weightingCheckpointState)) {
          break;
        }
        // tentative_confirmed_root = block_root
        tentativeConfirmedRoot = blockRoot;
      }
      // # the tentative_confirmed_root can only be confirmed if we can ensure that it is not
      // # going to be reorged out in either the current or next epoch.
      // if (get_block_epoch(store, tentative_confirmed_root) == current_epoch
      //     or (get_voting_source(store, tentative_confirmed_root).epoch + 2 >= current_epoch
      //         and (get_current_slot(store) % SLOTS_PER_EPOCH == 0
      //              or will_no_conflicting_checkpoint_be_justified(store,
      // get_checkpoint_block(store, head, current_epoch))))):
      // FIXME (spec): is it really the same condition as above?
      boolean isTentativeInCurrentEpoch =
          getBlockEpoch(store, tentativeConfirmedRoot).equals(currentEpoch);
      boolean isTentativeVoutingSourceTooOld =
          getVotingSource(store, tentativeConfirmedRoot)
              .getEpoch()
              .plus(2)
              .isLessThan(currentEpoch);
      if (isTentativeInCurrentEpoch
          || (isTentativeVoutingSourceTooOld
              && (isFirstEpochSlot || willNoConflictingCheckpointBeJustified))) {
        // confirmed_root = tentative_confirmed_root
        confirmedRoot = tentativeConfirmedRoot;
      }
    }
    // return confirmed_root
    return confirmedRoot;
  }

  // def get_latest_confirmed(store: Store) -> Root:
  Bytes32 getLatestConfirmed(
      ReadOnlyStore store,
      Bytes32 head /* TODO probably worth adding it to Store */,
      BeaconState weightingCheckpointState) {
    //    confirmed_root = store.confirmed_root
    Bytes32 confirmedRoot = store.getConfirmedRoot();
    //    current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentStoreEpoch(store);

    //    # revert to finalized block if the latest confirmed block:
    //    # a) from two or more epochs ago
    //    # b) doesn't belong to the canonical chain
    //    #
    //    # either of the above conditions signifies that confirmation rule assumptions (at least
    // synchrony) are broken
    //    # and already confirmed block might not be safe to use hence revert to the safest one
    // which is the finalized block
    //    # this reversal trades monotonicity in favour of safety in the casey of asynchrony in the
    // network
    //    head = get_head(store)
    //    if confirmed_block_epoch + 1 < current_epoch or not is_ancestor(store, head,
    // confirmed_root):
    // FIXME (spec): confirmed_block_epoch is not defined
    //
    //        confirmed_root = store.finalized_checkpoint.root
    UInt64 confirmedBlockEpoch = getBlockEpoch(store, confirmedRoot);
    if (confirmedBlockEpoch.increment().isLessThan(currentEpoch) || !isAncestor(store, head, confirmedRoot)) {
      confirmedRoot = store.getFinalizedCheckpoint().getRoot();
    }
    //
    //    # if we are at the beginning of the epoch and the epoch of the unrealized justified
    // checkpoint at beginning of the last slot of
    //    # the previous epoch corresponds to the previous epoch, then we can confirm the block of
    // the unrealized justified
    //    # checkpoint as, under synchrony, such a checkpoint is for sure now the greatest justified
    // checkpoint in the view
    //    # of any honest validator and, therefore, any honest validator will keep voting for it for
    // the entire epoch
    //    confirmed_block_slot = store.blocks[confirmed_root].slot
    UInt64 confirmedBlockSlot = store.getForkChoiceStrategy().blockSlot(confirmedRoot).orElseThrow();
    //    prev_unrealized_justified_checkpoint_slot =
    // store.blocks[store.prev_slot_unrealized_justified_checkpoint.root].slot
    UInt64 prevUnrealizedJustifiedCeckpointSlot = store
        .getForkChoiceStrategy()
        .blockSlot(store.getPrevSlotUnrealizedJustifiedCheckpoint().getRoot())
        .orElseThrow();
    UInt64 prevUnrealizedJustifiedCeckpointEpoch =
        store.getPrevSlotUnrealizedJustifiedCheckpoint().getEpoch();
    boolean isFirstEpochSlot = getCurrentSlot(store).mod(specConfig.getSlotsPerEpoch()).isZero();
    //    if (get_current_slot(store) % SLOTS_PER_EPOCH == 0
    //        and store.prev_slot_unrealized_justified_checkpoint.epoch + 1 == current_epoch
    //        and confirmed_block_slot < prev_unrealized_justified_checkpoint_slot):
    if (isFirstEpochSlot
        && prevUnrealizedJustifiedCeckpointEpoch.increment().equals(currentEpoch)
        && confirmedBlockSlot.isLessThan(prevUnrealizedJustifiedCeckpointSlot)) {
      //        confirmed_root = store.prev_slot_unrealized_justified_checkpoint.root
      confirmedRoot = store.getPrevSlotUnrealizedJustifiedCheckpoint().getRoot();
    }

    //    # attempt to further advance the latest confirmed block
    //    confirmed_block_epoch = compute_epoch_at_slot(store.blocks[confirmed_root].slot)
    confirmedBlockEpoch = getBlockEpoch(store, confirmedRoot);
    //    if confirmed_block_epoch + 1 >= current_epoch:
    //        return find_latest_confirmed_descendant(store, confirmed_root)
    //    else:
    //        return confirmed_root
    if (confirmedBlockEpoch.increment().isGreaterThanOrEqualTo(currentEpoch)) {
      return findLatestConfirmedDescendant(store, confirmedRoot, head, weightingCheckpointState);
    } else {
      return confirmedRoot;
    }
  }
}
