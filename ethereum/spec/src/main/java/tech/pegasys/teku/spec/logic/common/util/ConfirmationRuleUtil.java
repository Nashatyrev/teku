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
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class ConfirmationRuleUtil {

  private final SpecConfig specConfig;
  private final BeaconStateAccessors beaconStateAccessors;
  private final MiscHelpers miscHelpers;

  public ConfirmationRuleUtil(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
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

  private boolean isFirstEpochSlot(final UInt64 slot) {
    return computeSlotsSinceEpochStart(slot).isZero();
  }

  /**
   * Returns whether the range from ``first_slot`` to ``last_slot`` (inclusive of both) includes an
   * entire epoch
   */
  private boolean isFullValidatorSetCovered(final UInt64 firstSlot, final UInt64 lastSlot) {

    //    start_full_epoch = compute_epoch_at_slot(first_slot + (SLOTS_PER_EPOCH - 1))
    UInt64 startFullEpoch =
        miscHelpers.computeEpochAtSlot(firstSlot.plus(specConfig.getSlotsPerEpoch()).decrement());
    //    end_full_epoch = compute_epoch_at_slot(last_slot + 1) # exclusive
    UInt64 endFullEpoch = miscHelpers.computeEpochAtSlot(lastSlot.increment());

    //    return start_full_epoch < end_full_epoch
    return startFullEpoch.isLessThan(endFullEpoch);
  }

  /**
   * Returns the total weight of committees between ``start_slot`` and ``end_slot`` (inclusive of
   * both). FIX+ME: spec: name function estimate* instead of get* FIX+ME (spec): better do
   * `end_slot` exclusive instead of slot-1 on caller site
   */
  private UInt64 estimateCommitteeWeightBetweenSlots(
      BeaconState state, UInt64 firstSlot, UInt64 lastSlot) {

    //    total_active_balance = get_total_active_balance(state)
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(state);

    //    start_epoch = compute_epoch_at_slot(start_slot)
    //    end_epoch = compute_epoch_at_slot(end_slot)
    UInt64 startEpoch = miscHelpers.computeEpochAtSlot(firstSlot);
    UInt64 endEpoch = miscHelpers.computeEpochAtSlot(lastSlot);

    //    if start_slot > end_slot:
    //        return Gwei(0)
    if (firstSlot.isGreaterThan(lastSlot)) {
      return UInt64.ZERO;
    }

    UInt64 slotsPerEpoch = UInt64.valueOf(specConfig.getSlotsPerEpoch());
    //         end_epoch > start_epoch + 1
    //        or (end_epoch == start_epoch + 1 and start_slot % SLOTS_PER_EPOCH == 0))
    //    # If an entire epoch is covered by the range, return the total active balance
    //    if is_full_validator_set_covered(start_slot, end_slot):
    //        return total_active_balance
    if (isFullValidatorSetCovered(firstSlot, lastSlot)) {
      return totalActiveBalance;
    }

    //    if start_epoch == end_epoch:
    //        return total_active_balance // SLOTS_PER_EPOCH * (end_slot - start_slot + 1)
    if (startEpoch.equals(endEpoch)) {
      return totalActiveBalance
          .dividedBy(slotsPerEpoch)
          .times(lastSlot.minus(firstSlot).increment());
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
    // FIX+ME (!! spec) end_slot is inclusive then we need +1 here:
    //   num_slots_in_end_epoch = compute_slots_since_epoch_start(end_slot) + 1
    UInt64 numSlotsInEndEpoch = computeSlotsSinceEpochStart(lastSlot).increment();
    //        # Next, calculate the number of slots remaining in the end epoch
    //        remaining_slots_in_end_epoch = SLOTS_PER_EPOCH - num_slots_in_end_epoch
    UInt64 remainingSlotsInEndEpoch = slotsPerEpoch.minus(numSlotsInEndEpoch);
    //        # Then, calculate the number of slots in the start epoch
    //        num_slots_in_start_epoch = SLOTS_PER_EPOCH -
    // compute_slots_since_epoch_start(start_slot)
    UInt64 numSlotsInStartEpoch = slotsPerEpoch.minus(computeSlotsSinceEpochStart(firstSlot));
    //        end_epoch_weight_estimate = total_active_balance // SLOTS_PER_EPOCH *
    // num_slots_in_end_epoch
    UInt64 endEpochWeightEstimate =
        totalActiveBalance.dividedBy(slotsPerEpoch).times(numSlotsInEndEpoch);
    //        start_epoch_weight_estimate = (total_active_balance // SLOTS_PER_EPOCH //
    // SLOTS_PER_EPOCH *
    //            num_slots_in_start_epoch * remaining_slots_in_end_epoch)
    // FIX-ME: spec
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
    return estimate
        .dividedBy(1000)
        .times(1000 + specConfig.getCommitteeWeightEstimationAdjustmentFactor());
  }

  /** Fast prototype, but doesn't account referenceCheckpointState and thus is NOT spec compliant */
  private Optional<UInt64> getNetWeightForStateFast(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {
    // TODO consider referenceState. Most likely requires Protoarray redesign
    return store.getForkChoiceStrategy().getNetWeight(blockRoot);
  }

  /** Slow but spec compliant prototype */
  private UInt64 getNetWeightForStateSlow(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {

    //     unslashed_and_active_indices = [
    //        i for i in get_active_validator_indices(state, get_current_epoch(state))
    //        if not state.validators[i].slashed
    //    ]
    IntList activeValidatorIndices =
        beaconStateAccessors.getActiveNonSlashedValidatorIndices(
            referenceCheckpointState, getCurrentEpochStore(store));

    //    attestation_score = Gwei(sum(
    //        state.validators[i].effective_balance for i in unslashed_and_active_indices
    //        if (i in store.latest_messages
    //            and i not in store.equivocating_indices
    //            and is_ancestor(store, store.latest_messages[i].root, root))
    //    ))

    SszList<Validator> validators = referenceCheckpointState.getValidators();
    UInt64 attestationScore =
        store.calculateFromAllVotes(
            votes ->
                activeValidatorIndices.stream()
                    .filter(
                        validatorIndex -> {
                          if (validatorIndex >= votes.length) {
                            return false;
                          }
                          VoteTracker vote = votes[validatorIndex];
                          return vote != null
                              && !vote.equals(VoteTracker.DEFAULT)
                              && !vote.isEquivocating()
                              && isAncestor(store, vote.getNextRoot(), blockRoot);
                        })
                    .map(validatorIndex -> validators.get(validatorIndex).getEffectiveBalance())
                    .reduce(UInt64.ZERO, UInt64::plus));

    //    if store.proposer_boost_root == Root():
    //        # Return only attestation score if ``proposer_boost_root`` is not set
    //        return attestation_score
    //
    //    # Calculate proposer score if ``proposer_boost_root`` is set
    //    proposer_score = Gwei(0)
    //    # Boost is applied if ``root`` is an ancestor of ``proposer_boost_root``
    //    if is_ancestor(store, store.proposer_boost_root, root):
    //        proposer_score = get_proposer_score(store)
    //    return attestation_score + proposer_score

    // FIXME (spec): should we add proposer boost or not?
    return attestationScore;
  }

  private UInt64 getNetWeightForState(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {
    UInt64 weightFast =
        getNetWeightForStateFast(store, blockRoot, referenceCheckpointState).orElseThrow();
    UInt64 weightSlow = getNetWeightForStateSlow(store, blockRoot, referenceCheckpointState);
    checkState(
        weightFast.equals(weightSlow),
        "Weights doesnt match fast {} != slow {}",
        weightFast,
        weightSlow);
    return weightFast;
  }

  // def is_one_confirmed(store: Store, block_root: Root) -> bool:
  private boolean isOneConfirmed(
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
    // FIX+ME: (spec)
    //    if current_slot % SLOTS_PER_EPOCH == 0:
    //
    //        weighting_checkpoint = store.prev_slot_unrealized_justified_checkpoint
    //    else:
    //        weighting_checkpoint = store.prev_slot_justified_checkpoint
    //    weighting_checkpoint_state = store.checkpoint_states[weighting_checkpoint]

    //    support = get_weight(store, block_root, weighting_checkpoint_state)
    UInt64 support = getNetWeightForState(store, blockRoot, weightingCheckpointState);
    //    maximum_support = get_committee_weight_between_slots(
    //        weighting_checkpoint_state, Slot(parent_block.slot + 1), Slot(current_slot - 1))
    // FIX-ME (spec): if make end_slot exclusive then need to remove '- 1'
    UInt64 maximumSupport =
        estimateCommitteeWeightBetweenSlots(
            weightingCheckpointState,
            parentBlockSlot.increment(),
            getCurrentSlot(store).decrement());

    // FIXME (spec): is it ok that maximumSupport can be less than actual support???
    // checkState(support.isLessThanOrEqualTo(maximumSupport));
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
                .plus(
                    maximumSupport
                        .dividedBy(50)
                        .times(specConfig.getConfirmationByzantineThreshold()))
                .plus(proposerScore));
  }

  /** Compute the checkpoint block for epoch ``epoch`` in the chain of block ``root`` */
  private Bytes32 getCheckpointBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    UInt64 epochFirstSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return store.getForkChoiceStrategy().getAncestor(root, epochFirstSlot).orElseThrow();
  }

  private Checkpoint getCheckpointForBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    // FIXME spec deviation (looks equivalent)
    return new Checkpoint(epoch, getCheckpointBlock(store, root, epoch));
  }

  /** Uses LMD-GHOST votes to estimate FFG support for a checkpoint. */
  // def get_checkpoint_weight(store: Store, checkpoint: checkpoint_state, checkpoint_state:
  // BeaconState) -> Gwei:
  // FIX+ME (spec) fix checkpoint type
  private UInt64 getCheckpointWeightFast(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    UInt64 checkpointSlot = forkChoiceStrategy.blockSlot(checkpoint.getRoot()).orElseThrow();
    if (isFirstEpochSlot(checkpointSlot)) {
      // FIXME probably spec deviation
      // FIXME basically one need to use the state passed but the ProtoArray implied state,
      //  which is basically the current justified checkpoint state
      return forkChoiceStrategy.getNetWeight(checkpoint.getRoot()).orElseThrow();
    } else {
      // TODO maybe Protoarray modification is needed
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  /** Naive slow spec-like implementation */
  private UInt64 getCheckpointWeightSlow(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {

    //     if get_current_slot(store) <= compute_start_slot_at_epoch(checkpoint.epoch):
    //        return Gwei(0)
    if (getCurrentSlot(store)
        .isLessThanOrEqualTo(miscHelpers.computeStartSlotAtEpoch(checkpoint.getEpoch()))) {
      return UInt64.ZERO;
    }
    //    checkpoint_weight = 0
    //    for validator_index, latest_message in store.latest_messages.items():
    //        vote_target = get_checkpoint_for_block(store, latest_message.root,
    // latest_message.epoch)
    //        # checkpoint matches vote's target
    //        if checkpoint == vote_target:
    //            checkpoint_weight +=
    // checkpoint_state.validators[validator_index].effective_balance
    // FIXME (spec): nit: more function style
    SszList<Validator> validators = checkpointState.getValidators();
    UInt64 checkpointWeight =
        store.calculateFromAllVotes(
            votes ->
                IntStream.range(0, votes.length)
                    .filter(
                        validatorIndex -> {
                          VoteTracker vote = votes[validatorIndex];
                          return vote != null && !vote.equals(VoteTracker.DEFAULT);
                        })
                    .filter(
                        validatorIndex -> {
                          VoteTracker vote = votes[validatorIndex];
                          Checkpoint voteTarget =
                              getCheckpointForBlock(store, vote.getNextRoot(), vote.getNextEpoch());
                          return voteTarget.equals(checkpoint);
                        })
                    .mapToObj(
                        validatorIndex -> validators.get(validatorIndex).getEffectiveBalance())
                    .reduce(UInt64.ZERO, UInt64::plus));

    //    return Gwei(checkpoint_weight)
    return checkpointWeight;
  }

  private UInt64 getCheckpointWeight(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {
    UInt64 weightSlow = getCheckpointWeightSlow(store, checkpoint, checkpointState);

    UInt64 checkpointSlot =
        store.getForkChoiceStrategy().blockSlot(checkpoint.getRoot()).orElseThrow();
    if (isFirstEpochSlot(checkpointSlot)) {
      UInt64 weightFast = getCheckpointWeightFast(store, checkpoint, checkpointState);
      checkState(
          weightFast.equals(weightSlow),
          "Slow and fast algorithms don't match: {} != {}",
          weightSlow,
          weightFast);
    }
    return weightSlow;
  }

  // def get_ffg_weight_till_slot(slot: Slot, epoch: Epoch, total_active_balance: Gwei) -> Gwei:
  private UInt64 getFfgWeightTillSlot(UInt64 slot, UInt64 epoch, UInt64 totalActiveBalance) {
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
    // FIX+ME (spec): use computeSlotsSinceEpochStart
    UInt64 slotsPassed = computeSlotsSinceEpochStart(slot);
    return totalActiveBalance.dividedBy(specConfig.getSlotsPerEpoch()).times(slotsPassed);
  }

  // def will_current_epoch_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  private boolean willCurrentEpochCheckpointBeJustified(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {

    //    assert checkpoint.epoch == get_current_epoch_store(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);
    checkArgument(checkpoint.getEpoch().equals(currentEpoch));

    //    current_slot = get_current_slot(store)
    UInt64 currentSlot = getCurrentSlot(store);
    //    current_epoch = compute_epoch_at_slot(current_slot)
    //
    //    store_target_checkpoint_state(store, checkpoint)
    //    checkpoint_state = store.checkpoint_states[checkpoint]
    // FIXME (spec): to fix approach to retrieve state
    //
    //    total_active_balance = get_total_active_balance(checkpoint_state)
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(checkpointState);
    //
    //    # compute FFG support for checkpoint
    //    ffg_support_for_checkpoint = get_checkpoint_weight(store, checkpoint, checkpoint_state)
    UInt64 ffgSupportForCheckpoint = getCheckpointWeight(store, checkpoint, checkpointState);
    //
    //    # compute total FFG weight till current slot
    //    ffg_weight_till_now = get_ffg_weight_till_slot(current_slot, current_epoch,
    // total_active_balance)
    UInt64 ffgWeightTillNow = getFfgWeightTillSlot(currentSlot, currentEpoch, totalActiveBalance);

    //    # compute remaining honest FFG weight
    //    remaining_ffg_weight = total_active_balance - ffg_weight_till_now
    UInt64 remainingFfgWeight = totalActiveBalance.minus(ffgWeightTillNow);
    //    remaining_honest_ffg_weight = Gwei(remaining_ffg_weight // 100 * (100 -
    // config.CONFIRMATION_BYZANTINE_THRESHOLD))
    UInt64 remainingHonestFfgWeight =
        remainingFfgWeight
            .dividedBy(100)
            .times(100 - specConfig.getConfirmationByzantineThreshold());

    //    # compute min honest FFG support
    //    min_honest_ffg_support = ffg_support_for_checkpoint - min(
    //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_BYZANTINE_THRESHOLD),
    //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_SLASHING_THRESHOLD),
    //        ffg_support_for_checkpoint
    //    )
    UInt64 min =
        Stream.of(
                ffgWeightTillNow
                    .dividedBy(100)
                    .times(specConfig.getConfirmationByzantineThreshold()),
                ffgWeightTillNow
                    .dividedBy(100)
                    .times(specConfig.getConfirmationSlashingThreshold()),
                ffgSupportForCheckpoint)
            .min(Comparator.naturalOrder())
            .orElseThrow();
    UInt64 minHonestFfgSupport = ffgSupportForCheckpoint.minus(min);

    //    return 3 * (min_honest_ffg_support + remaining_honest_ffg_weight) >= 2 *
    // total_active_balance
    return minHonestFfgSupport
        .plus(remainingHonestFfgWeight)
        .times(3)
        .isGreaterThanOrEqualTo(totalActiveBalance.times(2));
  }

  public Checkpoint getUnrealizedJustifiedCheckpoint(ReadOnlyStore store) {
    // FIXME alternative view of store.unrealized_justified_checkpoint. Need to double check
    Checkpoint ret =
        store.getForkChoiceStrategy().getChainHeads(true).stream()
            .map(
                head -> {
                  return head.getCheckpoints().getUnrealizedJustifiedCheckpoint();
                })
            .max(Comparator.comparing(Checkpoint::getEpoch))
            .orElseThrow();

    // FIXME: hack around initial ZERO checkpoints
    if (ret.getRoot().equals(Bytes32.ZERO)) {
      Bytes32 genesisBlockRoot =
          store.getForkChoiceStrategy().getBlockRootsAtSlot(UInt64.ZERO).getFirst();
      return new Checkpoint(UInt64.ZERO, genesisBlockRoot);
    } else {
      return ret;
    }
  }

  // def will_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  private boolean willCheckpointBeJustified(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {
    //    if checkpoint == store.justified_checkpoint:
    //        return True
    if (checkpoint.equals(store.getJustifiedCheckpoint())) {
      return true;
    }
    //    if checkpoint == store.unrealized_justified_checkpoint:
    //        return True
    if (checkpoint.equals(getUnrealizedJustifiedCheckpoint(store))) {
      return true;
    }
    //    if checkpoint.epoch == get_current_epoch_store(store):
    //        return will_current_epoch_checkpoint_be_justified(store, checkpoint)
    if (checkpoint.getEpoch().equals(getCurrentEpochStore(store))) {
      return willCurrentEpochCheckpointBeJustified(store, checkpoint, checkpointState);
    }
    //    return False
    return false;
  }

  //
  // def will_no_conflicting_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  private boolean willNoConflictingCheckpointBeJustified(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {
    //    assert checkpoint.epoch == get_current_epoch_store(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);
    checkArgument(checkpoint.getEpoch().equals(currentEpoch));

    //    current_slot = get_current_slot(store)
    UInt64 currentSlot = getCurrentSlot(store);
    //    current_epoch = compute_epoch_at_slot(current_slot)

    //    store_target_checkpoint_state(store, checkpoint)
    //    checkpoint_state = store.checkpoint_states[checkpoint]
    // FIXME (spec): to fix approach to retrieve state
    //
    //    total_active_balance = get_total_active_balance(checkpoint_state)
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(checkpointState);

    //    # compute FFG support for checkpoint
    //    ffg_support_for_checkpoint = get_checkpoint_weight(store, checkpoint, checkpoint_state)
    UInt64 ffgSupportForCheckpoint = getCheckpointWeight(store, checkpoint, checkpointState);

    //    # compute total FFG weight till current slot
    //    ffg_weight_till_now = get_ffg_weight_till_slot(current_slot, current_epoch,
    // total_active_balance)
    UInt64 ffgWeightTillNow = getFfgWeightTillSlot(currentSlot, currentEpoch, totalActiveBalance);

    //    # compute remaining honest FFG weight
    //    remaining_ffg_weight = total_active_balance - ffg_weight_till_now
    UInt64 remainingFfgWeight = totalActiveBalance.minus(ffgWeightTillNow);
    //    remaining_honest_ffg_weight = Gwei(remaining_ffg_weight // 100 * (100 -
    // config.CONFIRMATION_BYZANTINE_THRESHOLD))
    UInt64 remainingHonestFfgWeight =
        remainingFfgWeight
            .dividedBy(100)
            .times(100 - specConfig.getConfirmationByzantineThreshold());

    //    # compute min honest FFG support
    //    min_honest_ffg_support = ffg_support_for_checkpoint - min(
    //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_BYZANTINE_THRESHOLD),
    //        Gwei(ffg_weight_till_now // 100 * config.CONFIRMATION_SLASHING_THRESHOLD),
    //        ffg_support_for_checkpoint
    //    )
    UInt64 min =
        Stream.of(
                ffgWeightTillNow
                    .dividedBy(100)
                    .times(specConfig.getConfirmationByzantineThreshold()),
                ffgWeightTillNow
                    .dividedBy(100)
                    .times(specConfig.getConfirmationSlashingThreshold()),
                ffgSupportForCheckpoint)
            .min(Comparator.naturalOrder())
            .orElseThrow();
    UInt64 minHonestFfgSupport = ffgSupportForCheckpoint.minus(min);

    //    return 3 * (min_honest_ffg_support + remaining_honest_ffg_weight) >= total_active_balance
    return minHonestFfgSupport
        .plus(remainingHonestFfgWeight)
        .times(3)
        .isGreaterThanOrEqualTo(totalActiveBalance);
    // FIX+ME (spec): deduplicate function with willCurrentEpochCheckpointBeJustified (just the
    //    latest multiplier differs)
    // https://github.com/mkalinin/confirmation-rule/pull/23
  }

  private UInt64 getBlockEpoch(ReadOnlyStore store, Bytes32 blockRoot) {
    UInt64 blockSlot = store.getForkChoiceStrategy().blockSlot(blockRoot).orElseThrow();
    return miscHelpers.computeEpochAtSlot(blockSlot);
  }

  private UInt64 getCurrentEpochStore(ReadOnlyStore store) {
    UInt64 currentSlot = getCurrentSlot(store);
    return miscHelpers.computeEpochAtSlot(currentSlot);
  }

  private List<Bytes32> getChainRoots(
      ReadOnlyStore store, UInt64 ancestorSlot, Bytes32 startBlockRoot) {
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
    UInt64 currentEpoch = getCurrentEpochStore(store);
    // block_epoch = compute_epoch_at_slot(block.slot)
    UInt64 blockEpoch = getBlockEpoch(store, blockRoot);
    // if current_epoch > block_epoch:
    BlockCheckpoints blockCheckpoints =
        store.getForkChoiceStrategy().getBlockData(blockRoot).orElseThrow().getCheckpoints();
    if (currentEpoch.isGreaterThan(blockEpoch)) {
      // # The block is from a prior epoch, the voting source will be pulled-up
      // return store.unrealized_justifications[block_root]
      // FIXME: different from spec but looks equivalent
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
  private boolean isAncestor(ReadOnlyStore store, Bytes32 root, Bytes32 ancestor) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    UInt64 ancestorSlot = forkChoiceStrategy.blockSlot(ancestor).orElseThrow();
    return forkChoiceStrategy.getAncestor(root, ancestorSlot).orElseThrow().equals(ancestor);
  }

  /**
   * This function assumes that the ``latest_confirmed_root`` belongs to the canonical chain and is
   * either from the previous or from the current epoch.
   */
  // def find_latest_confirmed_descendant(store: Store, latest_confirmed_root: Root) -> Root:
  private Bytes32 findLatestConfirmedDescendant(
      ReadOnlyStore store,
      Bytes32 latestConfirmedRoot,
      Bytes32 headBlockRoot,
      BeaconState weightingCheckpointState) {
    // current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);
    // # verify the latest confirmed block is not too old
    // assert compute_block_epoch(latest_confirmed_root) + 1 >= current_epoch
    // FIX+ME (spec) no compute_block_epoch() function found
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
    boolean isFirstEpochSlot = isFirstEpochSlot(getCurrentSlot(store));
    // FIX+ME sepc deviation with getCheckpointForBlock
    // https://github.com/mkalinin/confirmation-rule/pull/22
    boolean willNoConflictingCheckpointBeJustified =
        willNoConflictingCheckpointBeJustified(
            store,
            getCheckpointForBlock(store, headBlockRoot, currentEpoch),
            weightingCheckpointState);
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
      // FIX+ME (spec) confirmed_root -> slot ?
      List<Bytes32> canonicalRoots = getChainRoots(store, confirmedSlot, headBlockRoot);
      // assert canonical_roots.pop(0) == confirmed_root
      checkArgument(canonicalRoots.getFirst().equals(confirmedRoot));
      canonicalRoots.removeFirst();
      // # starting with the child of the latest_confirmed_root
      // # move towards the head in attempt to advance confirmed block
      // # and stop when the first unconfirmed descendant is encountered for block_root in
      // canonical_roots:
      for (Bytes32 blockRoot : canonicalRoots) {
        // block_epoch = compute_epoch_at_slot(store.blocks[block_root].slot)
        // FIX+ME (spec): can replace with get_block_epoch()
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
      UInt64 confirmedSlot1 = forkChoiceStrategy.blockSlot(confirmedRoot).orElseThrow();
      List<Bytes32> canonicalRoots = getChainRoots(store, confirmedSlot1, headBlockRoot);
      // assert canonical_roots.pop(0) == confirmed_root
      checkArgument(canonicalRoots.getFirst().equals(confirmedRoot));
      canonicalRoots.removeFirst();
      // tentative_confirmed_root = confirmed_root
      Bytes32 tentativeConfirmedRoot = confirmedRoot;
      // for block_root in canonical_roots:
      //     block_epoch = compute_epoch_at_slot(store.blocks[block_root].slot)
      // FIX+ME (spec): can replace with get_block_epoch()
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
          if (!willCheckpointBeJustified(store, checkpoint, weightingCheckpointState)) {
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

  private LRUCache<Bytes32, UInt64> signleBlockRootToEpochCache =
      LRUCache.<Bytes32, UInt64>create(1);

  // the previous confirmed root was evicted due to finalization of a later block
  // this is a hacky way to preserve it for just one iteration
  private UInt64 getBlockEpochCached(ReadOnlyStore store, Bytes32 blockRoot) {
    return signleBlockRootToEpochCache.get(blockRoot, root -> getBlockEpoch(store, root));
  }

  // def get_latest_confirmed(store: Store) -> Root:
  public Bytes32 getLatestConfirmed(
      ReadOnlyStore store,
      Bytes32 head /* TODO probably worth adding it to Store */,
      BeaconState weightingCheckpointState) {
    //    confirmed_root = store.confirmed_root
    Bytes32 confirmedRoot = store.getConfirmedRoot();
    //    current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);

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
    // FIX+ME (spec): confirmed_block_epoch is not defined
    //
    //        confirmed_root = store.finalized_checkpoint.root
    UInt64 confirmedBlockEpoch = getBlockEpochCached(store, confirmedRoot);
    if (confirmedBlockEpoch.increment().isLessThan(currentEpoch)
        || !isAncestor(store, head, confirmedRoot)) {
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
    UInt64 confirmedBlockSlot =
        store.getForkChoiceStrategy().blockSlot(confirmedRoot).orElseThrow();
    //    prev_unrealized_justified_checkpoint_slot =
    // store.blocks[store.prev_slot_unrealized_justified_checkpoint.root].slot
    UInt64 prevUnrealizedJustifiedCeckpointSlot =
        store
            .getForkChoiceStrategy()
            .blockSlot(store.getPrevSlotUnrealizedJustifiedCheckpoint().getRoot())
            .orElseThrow();
    UInt64 prevUnrealizedJustifiedCeckpointEpoch =
        store.getPrevSlotUnrealizedJustifiedCheckpoint().getEpoch();
    boolean isFirstEpochSlot = isFirstEpochSlot(getCurrentSlot(store));
    //    if (get_current_slot(store) % SLOTS_PER_EPOCH == 0
    //        and store.prev_slot_unrealized_justified_checkpoint.epoch + 1 == current_epoch
    //        and confirmed_block_slot < prev_unrealized_justified_checkpoint_slot):
    // FIX+ME (spec): isFirstEpochSlot
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
      confirmedRoot =
          findLatestConfirmedDescendant(store, confirmedRoot, head, weightingCheckpointState);
    }
    // put to cache
    getBlockEpochCached(store, confirmedRoot);
    return confirmedRoot;
  }
}
