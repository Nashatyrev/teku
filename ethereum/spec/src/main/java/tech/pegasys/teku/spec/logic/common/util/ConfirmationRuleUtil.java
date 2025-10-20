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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class ConfirmationRuleUtil {

  private static final boolean DEBUG_PRINT = true;

  public interface CheckpointStateStore {

    BeaconState getState(Checkpoint checkpoint);
  }

  public static class TrackingCheckpointStateStore implements CheckpointStateStore {
    private final CheckpointStateStore delegate;
    private final List<Checkpoint> requestedCheckpoints = new ArrayList<>();

    public TrackingCheckpointStateStore(CheckpointStateStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public BeaconState getState(Checkpoint checkpoint) {
      requestedCheckpoints.add(checkpoint);
      return delegate.getState(checkpoint);
    }

    public List<Checkpoint> getRequestedCheckpoints() {
      return requestedCheckpoints;
    }
  }

  private final SpecConfig specConfig;
  private final BeaconStateAccessors beaconStateAccessors;
  private final BeaconStateUtil beaconStateUtil;
  private final MiscHelpers miscHelpers;

  public ConfirmationRuleUtil(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      BeaconStateUtil beaconStateUtil,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateUtil = beaconStateUtil;
    this.miscHelpers = miscHelpers;
  }

  public UInt64 getCurrentSlot(final ReadOnlyStore store) {
    return miscHelpers.computeSlotAtTime(store.getGenesisTime(), store.getTimeSeconds());
  }

  private UInt64 computeSlotsSinceEpochStart(final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final UInt64 epochStartSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return slot.minus(epochStartSlot);
  }

  public boolean isFirstEpochSlot(final UInt64 slot) {
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

  // implements the following spec statement:
  //        store.unrealized_justified_checkpoint
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

  private List<Bytes32> getChainRoots(
      ReadOnlyStore store, Bytes32 ancestorRootInclusive, Bytes32 startBlockRootInclusive) {
    return getChainRoots(
        store,
        store.getForkChoiceStrategy().blockSlot(ancestorRootInclusive).orElseThrow(),
        startBlockRootInclusive);
  }

  private List<Bytes32> getChainRoots(
      ReadOnlyStore store, UInt64 ancestorSlotInclusive, Bytes32 startBlockRootInclusive) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    ArrayList<Bytes32> ret = new ArrayList<>();
    Bytes32 root = startBlockRootInclusive;
    ret.add(root);
    while (forkChoiceStrategy.blockSlot(root).orElseThrow().isGreaterThan(ancestorSlotInclusive)) {
      root = forkChoiceStrategy.blockParentRoot(root).orElseThrow();
      ret.add(root);
    }
    return ret.reversed();
  }

  private record IndexedVote(UInt64 index, VoteTracker vote) {
    public boolean isNotNull() {
      return vote() != null && !vote().equals(VoteTracker.DEFAULT);
    }
  }

  private Stream<IndexedVote> streamIndexedVotes(ReadOnlyStore store) {
    // FIXME: leaking stream from under the read lock
    return store.calculateFromAllVotes(
        votes ->
            IntStream.range(0, votes.length)
                .mapToObj(i -> new IndexedVote(UInt64.valueOf(i), votes[i]))
                .filter(IndexedVote::isNotNull));
  }

  private Set<UInt64> filterNonEquivocatingValidatorVotes(
      ReadOnlyStore store, Predicate<IndexedVote> filter) {
    return streamIndexedVotes(store)
        .filter(iv -> !iv.vote().isEquivocating() && filter.apply(iv))
        .map(iv -> iv.index)
        .collect(Collectors.toUnmodifiableSet());
  }

  private List<IndexedVote> getEquivocatingVotes(ReadOnlyStore store) {
    return streamIndexedVotes(store).filter(iv -> iv.vote().isEquivocating()).toList();
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

  // def get_equivocation_score(store: Store, balance_source: BeaconState, first_slot: Slot,
  // last_slot: Slot) -> Gwei:
  private UInt64 getEquivocationScore(
      ReadOnlyStore store,
      BeaconState balanceSource,
      UInt64 firstSlot,
      UInt64 lastSlot) {

    List<IndexedVote> equivocatingVotes = getEquivocatingVotes(store);

    if (equivocatingVotes.isEmpty()) {
      return UInt64.ZERO;
    }

    //    equivocating_indices = committee_indices.intersection(store.equivocating_indices)
    //    return Gwei(
    //        sum(balance_source.validators[i].effective_balance for i in equivocating_indices)
    //    )
    return equivocatingVotes.stream()
        //        .filter(committeeIndices::contains)
        .filter(
            iv ->
                iv.vote().getNextSlot().isGreaterThanOrEqualTo(firstSlot)
                    && iv.vote().getNextSlot().isLessThanOrEqualTo(lastSlot))
        .map(iv -> balanceSource.getValidators().get(iv.index().intValue()).getEffectiveBalance())
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  // def compute_adversarial_weight(store: Store, balance_source: BeaconState, first_slot: Slot,
  // last_slot: Slot) -> Gwei:
  private UInt64 computeAdversarialWeight(
      ReadOnlyStore store,
      BeaconState balanceSource,
      UInt64 firstSlot,
      UInt64 lastSlot) {
    //    maximum_weight = estimate_committee_weight_between_slots(balance_source, first_slot,
    // last_slot)
    UInt64 maximumWeight = estimateCommitteeWeightBetweenSlots(balanceSource, firstSlot, lastSlot);
    //    max_adversarial_weight = maximum_weight // 100 * CONFIRMATION_BYZANTINE_THRESHOLD
    UInt64 maxAdversarialWeight =
        maximumWeight.dividedBy(100).times(specConfig.getConfirmationByzantineThreshold());

    //    # Discount total weight of equivocating validators
    //    equivocation_score = get_equivocation_score(store, balance_source, first_slot, last_slot)
    UInt64 equivocationScore =
        getEquivocationScore(store, balanceSource, firstSlot, lastSlot);
    //    if max_adversarial_weight > equivocation_score:
    //        return Gwei(max_adversarial_weight - equivocation_score)
    //    else:
    //        return Gwei(0)
    return maxAdversarialWeight.minusMinZero(equivocationScore);
  }

  // def get_block_support_in_slots(balance_source: BeaconState, block_root: Root, first_slot: Slot,
  // last_slot: Slot) -> Gwei:
  UInt64 getBlockSupportInSlots(
      ReadOnlyStore store,
      BeaconState balanceSource,
      Bytes32 blockRoot,
      UInt64 firstSlot,
      UInt64 lastSlot) {
    //    committees = []
    //    for slot in range(first_slot, last_slot + 1):
    //        committees.append(get_slot_committee(store, slot))
    //    unslashed_and_active_committee_indices = [
    //        i for i in get_active_validator_indices(balance_source,
    // get_current_epoch(balance_source))
    //        if (i in committees and not balance_source.validators[i].slashed)
    //    ]
    //    return Gwei(sum(
    //        balance_source.validators[i].effective_balance for i in
    // unslashed_and_active_committee_indices
    //        if (i in store.latest_messages
    //            and i not in store.equivocating_indices
    //            and store.latest_messages[i].root == block_root)
    //    ))
    Set<UInt64> supportValidators =
        filterNonEquivocatingValidatorVotes(
            store,
            iv ->
                iv.vote.getNextRoot().equals(blockRoot)
                    && iv.vote().getNextSlot().isGreaterThanOrEqualTo(firstSlot)
                    && iv.vote().getNextSlot().isLessThanOrEqualTo(firstSlot));
    return sumUnslashedBalances(store, supportValidators, balanceSource);
  }

  // def compute_empty_slot_support_discount(store: Store, balance_source: BeaconState, block_root:
  // Root) -> Gwei:
  private UInt64 computeEmptySlotSupportDiscount(
      ReadOnlyStore store,
      BeaconState balanceSource,
      Bytes32 blockRoot) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    //    # No empty slot
    UInt64 blockSlot = forkChoiceStrategy.blockSlot(blockRoot).orElseThrow();
    Bytes32 parentRoot = forkChoiceStrategy.blockParentRoot(blockRoot).orElseThrow();
    UInt64 parentSlot = forkChoiceStrategy.blockSlot(parentRoot).orElseThrow();
    //    if parent_block.slot + 1 == block.slot:
    //        return Gwei()
    if (parentSlot.increment().equals(blockSlot)) {
      return UInt64.ZERO;
    }

    //    # Discount votes supporting the parent block during empty slots
    //    # with exception to the adversarial fraction
    //    parent_support_in_empty_slots = get_block_support_in_slots(
    //        balance_source, block.parent_root, parent_block.slot + 1, block.slot - 1)
    UInt64 parentSupportInEmptySlots =
        getBlockSupportInSlots(
            store,
            balanceSource,
            parentRoot,
            parentSlot.increment(),
            blockSlot.decrement());
    //    adversarial_weight = compute_adversarial_weight(
    //        store, balance_source, parent_block.slot + 1, block.slot - 1)
    UInt64 adversarialWeight =
        computeAdversarialWeight(
            store, balanceSource, parentSlot.increment(), blockSlot.decrement());
    //    if parent_support_in_empty_slots > adversarial_weight:
    //        return parent_support_in_empty_slots - adversarial_weight
    //    else:
    //        return Gwei(0)
    return parentSupportInEmptySlots.minusMinZero(adversarialWeight);
  }

  // def get_support_discount(store: Store, balance_source: BeaconState, block_root: Root) -> Gwei:
  private UInt64 getSupportDiscount(
      ReadOnlyStore store,
      BeaconState balanceSource,
      Bytes32 blockRoot) {
    //    # Empty slot support discount
    //    empty_slot_support = compute_empty_slot_support_discount(store, balance_source,
    // block_root)
    UInt64 emptySlotSupport =
        computeEmptySlotSupportDiscount(store, balanceSource, blockRoot);
    //    # Parent block support during the block's slot
    //    parent_block_support = get_block_support_in_slots(
    //        balance_source, block.parent_root, block.slot, block.slot)
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    UInt64 blockSlot = forkChoiceStrategy.blockSlot(blockRoot).orElseThrow();
    Bytes32 parentRoot = forkChoiceStrategy.blockParentRoot(blockRoot).orElseThrow();
    UInt64 parentBlockSupport =
        getBlockSupportInSlots(
            store, balanceSource, parentRoot, blockSlot, blockSlot);
    //    return empty_slot_support + parent_block_support
    return emptySlotSupport.plus(parentBlockSupport);
  }

  /** Fast prototype, but doesn't account referenceCheckpointState and thus is NOT spec compliant */
  private Optional<UInt64> getAttestationScoreFast(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {
    return store.getForkChoiceStrategy().getNetWeight(blockRoot);
  }

  private UInt64 sumUnslashedBalances(
      ReadOnlyStore store, Set<UInt64> validatorIndices, BeaconState referenceCheckpointState) {

    List<UInt64> effectiveActiveUnslashedBalances =
        beaconStateUtil.getEffectiveActiveUnslashedBalances(
            referenceCheckpointState, getCurrentEpochStore(store));
    return IntStream.range(0, effectiveActiveUnslashedBalances.size())
        .filter(i -> !effectiveActiveUnslashedBalances.get(i).isZero())
        .filter(i -> validatorIndices.contains(UInt64.valueOf(i)))
        .mapToObj(effectiveActiveUnslashedBalances::get)
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  private class IsAncestorCachedChecker {
    private final ReadOnlyStore store;
    private final Bytes32 ancestorRoot;
    private final Map<Bytes32, Boolean> isAncestorCache = new HashMap<>();

    public IsAncestorCachedChecker(ReadOnlyStore store, Bytes32 ancestorRoot) {
      this.store = store;
      this.ancestorRoot = ancestorRoot;
    }

    public boolean isDescendant(Bytes32 descendantRoot) {
      Boolean b = isAncestorCache.get(descendantRoot);
      if (b == null) {
        b = isAncestor(store, descendantRoot, ancestorRoot);
        isAncestorCache.put(descendantRoot, b);
      }
      return b;
    }
  }

  /** Slow but spec compliant prototype */
  private UInt64 getAttestationScoreSlow(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {

    IsAncestorCachedChecker isAncestorChecker = new IsAncestorCachedChecker(store, blockRoot);
    Set<UInt64> filteredValidatorIndices =
        filterNonEquivocatingValidatorVotes(
            store, iv -> isAncestorChecker.isDescendant(iv.vote().getNextRoot()));
    UInt64 ret = sumUnslashedBalances(store, filteredValidatorIndices, referenceCheckpointState);
    return ret;
  }

  private UInt64 getAttestationScore(
      ReadOnlyStore store, Bytes32 blockRoot, BeaconState referenceCheckpointState) {
    //        UInt64 weightFast =
    //            getAttestationScoreFast(store, blockRoot, referenceCheckpointState).orElseThrow();
    UInt64 weightSlow = getAttestationScoreSlow(store, blockRoot, referenceCheckpointState);
    //    checkState(
    //        weightFast.equals(weightSlow),
    //        "Weights doesnt match fast {} != slow {}",
    //        weightFast,
    //        weightSlow);
    return weightSlow;
  }

  /** Naive slow spec-like implementation */
  private UInt64 getCheckpointWeightSlow(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {

    CheckpointFast checkpointFast = CheckpointFast.fromCheckpoint(checkpoint);
    Set<UInt64> filteredValidatorIndices =
        filterNonEquivocatingValidatorVotes(
            store,
            iv -> {
              if (!store.getForkChoiceStrategy().contains(iv.vote().getNextRoot())) {
                // vote block was finalized and pruned from protoarray
                return false;
              }
              CheckpointFast voteTarget =
                  getCheckpointFastForBlock(
                      store, iv.vote().getNextRoot(), iv.vote().getNextEpoch());
              return voteTarget.equals(checkpointFast);
            });
    return sumUnslashedBalances(store, filteredValidatorIndices, checkpointState);
  }

  @VisibleForTesting
  public UInt64 getCheckpointWeight(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState checkpointState) {

    //    UInt64 checkpointSlot =
    //        store.getForkChoiceStrategy().blockSlot(checkpoint.getRoot()).orElseThrow();
    //    UInt64 checkpointBlockEpoch = miscHelpers.computeEpochAtSlot(checkpointSlot);
    //    if (checkpoint.getEpoch().equals(checkpointBlockEpoch)) {
    //      // just checking implementation compatibility
    //      UInt64 weightSlow = getCheckpointWeightSlow(store, checkpoint, checkpointState, false,
    // false);
    //      UInt64 weightFast = getCheckpointWeightFast(store, checkpoint, checkpointState);
    //      checkState(
    //          weightFast.equals(weightSlow),
    //          "Slow and fast algorithms don't match: {} != {}",
    //          weightSlow,
    //          weightFast);
    //    }

    UInt64 weightSlow = getCheckpointWeightSlow(store, checkpoint, checkpointState);
    return weightSlow;
  }

  // def is_one_confirmed(store: Store, block_root: Root) -> bool:
  private boolean isOneConfirmed(
      final ReadOnlyStore store,
      final Bytes32 blockRoot,
      final CheckpointStateStore checkpointStateStore) {
    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();

    UInt64 blockSlot = forkChoiceStrategy.blockSlot(blockRoot).orElseThrow();
    if (blockSlot.equals(getCurrentSlot(store))) {
      return false;
    }
    //    current_slot = get_current_slot(store)
    //    block = store.blocks[block_root]
    //    parent_block = store.blocks[block.parent_root]
    Bytes32 parentBlockRoot = forkChoiceStrategy.blockParentRoot(blockRoot).orElseThrow();
    UInt64 parentBlockSlot = forkChoiceStrategy.blockSlot(parentBlockRoot).orElseThrow();

    BeaconState balanceSource =
        checkpointStateStore.getState(store.getPrevEpochUnrealizedJustifiedCheckpoint());
    UInt64 support = getAttestationScore(store, blockRoot, balanceSource);
    UInt64 proposerScore = beaconStateAccessors.getProposerBoostAmount(balanceSource);
    UInt64 maximumSupport =
        estimateCommitteeWeightBetweenSlots(
            balanceSource, parentBlockSlot.increment(), getCurrentSlot(store).decrement());
    UInt64 supportDiscount = getSupportDiscount(store, balanceSource, blockRoot);
    UInt64 adversarialWeight =
        computeAdversarialWeight(
            store, balanceSource, blockSlot, getCurrentSlot(store).decrement());

    if (DEBUG_PRINT) {
      //      double qLeft = support.doubleValue() / maximumSupport.doubleValue();
      //      double qRight =
      //          0.5d
      //                  * (1.0d
      //                      + (proposerScore.doubleValue() - honestParentSupport.doubleValue())
      //                          / maximumSupport.doubleValue())
      //              + specConfig.getConfirmationByzantineThreshold() / 100.0d;
      //      System.err.println(
      //          "    "
      //              + blockSlot
      //              + ": "
      //              + qLeft
      //              + " <> "
      //              + qRight
      //              + " ("
      //              + uint2str(support)
      //              + " / "
      //              + uint2str(maximumSupport)
      //              + " <> 0.5 * (1 + ("
      //              + uint2str(proposerScore)
      //              + " - "
      //              + uint2str(honestParentSupport)
      //              + ") / "
      //              + uint2str(maximumSupport)
      //              + ") + "
      //              + specConfig.getConfirmationByzantineThreshold() / 100.0d
      //              + ")");
    }

    // (support - proposer_score - adversarial_weight + support_discount) / maximum_support > 1/2
    return support
        .times(2)
        .plus(supportDiscount)
        .isGreaterThan(maximumSupport.plus(proposerScore).plus(adversarialWeight.times(2)));
  }

  private static String uint2str(UInt64 uint) {
    return String.format("%,d", uint.longValue());
  }

  private boolean isChainReconfirmed(
      final ReadOnlyStore store,
      final Bytes32 confirmedRoot,
      final CheckpointStateStore checkpointStateStore) {

    ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();

    if (!isAncestor(
        store, confirmedRoot, store.getPrevEpochUnrealizedJustifiedCheckpoint().getRoot())) {
      return false;
    }

    UInt64 currentEpoch = getCurrentEpochStore(store);
    final Bytes32 startRoot;
    //     if store.prev_epoch_unrealized_justified_checkpoint.epoch + 1 >= current_epoch:
    if (store
        .getPrevEpochUnrealizedJustifiedCheckpoint()
        .getEpoch()
        .increment()
        .isGreaterThanOrEqualTo(currentEpoch)) {
      startRoot = store.getPrevEpochUnrealizedJustifiedCheckpoint().getRoot();
    } else {
      Checkpoint checkpoint = getCheckpointForBlock(store, confirmedRoot, currentEpoch.decrement());
      startRoot = forkChoiceStrategy.blockParentRoot(checkpoint.getRoot()).orElseThrow();
    }

    return getChainRoots(store, startRoot, confirmedRoot).stream()
        .skip(1)
        .allMatch(root -> isOneConfirmed(store, root, checkpointStateStore));
  }

  /** Compute the checkpoint block for epoch ``epoch`` in the chain of block ``root`` */
  private Bytes32 getCheckpointBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    UInt64 epochFirstSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return store.getForkChoiceStrategy().getAncestor(root, epochFirstSlot).orElseThrow();
  }

  private Checkpoint getCheckpointForBlock(ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    // FIX+ME spec deviation (looks equivalent)
    // https://github.com/mkalinin/confirmation-rule/pull/22
    return new Checkpoint(epoch, getCheckpointBlock(store, root, epoch));
  }

  LRUCache<Pair<Bytes32, UInt64>, CheckpointFast> blockCheckpointCache = LRUCache.create(10000);

  private CheckpointFast getCheckpointFastForBlock(
      ReadOnlyStore store, Bytes32 root, UInt64 epoch) {
    return blockCheckpointCache.get(
        Pair.of(root, epoch),
        __ -> CheckpointFast.fromCheckpoint(getCheckpointForBlock(store, root, epoch)));
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
      return getAttestationScore(store, checkpoint.getRoot(), checkpointState);
    } else {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  record CheckpointFast(int epoch, Bytes32 root) {
    static CheckpointFast fromCheckpoint(Checkpoint checkpoint) {
      return new CheckpointFast(checkpoint.getEpoch().intValue(), checkpoint.getRoot());
    }
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

  private UInt64 computeHonestFfgSupport(
      ReadOnlyStore store, Checkpoint checkpoint, BeaconState state) {

    //    assert checkpoint.epoch == get_current_epoch_store(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);
    checkArgument(checkpoint.getEpoch().equals(currentEpoch));

    //    current_slot = get_current_slot(store)
    UInt64 currentSlot = getCurrentSlot(store);
    //    current_epoch = compute_epoch_at_slot(current_slot)

    //    total_active_balance = get_total_active_balance(checkpoint_state)
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(state);
    //
    //    # compute FFG support for checkpoint
    //    ffg_support_for_checkpoint = get_checkpoint_weight(store, checkpoint, checkpoint_state)
    UInt64 ffgSupportForCheckpoint = getCheckpointWeight(store, checkpoint, state);
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

    // In real numbers:
    //   (min_honest_ffg_support + remaining_honest_ffg_weight) / total_active_balance >= 2 / 3
    //    return 3 * (min_honest_ffg_support + remaining_honest_ffg_weight) >= 2 *
    // total_active_balance
    return minHonestFfgSupport.plus(remainingHonestFfgWeight);
  }

  // def will_no_conflicting_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  private boolean willNoConflictingCheckpointBeJustified(
      ReadOnlyStore store, Checkpoint checkpoint, CheckpointStateStore checkpointStateStore) {

    checkArgument(checkpoint.getEpoch().equals(getCurrentEpochStore(store)));

    if (checkpoint.equals(getUnrealizedJustifiedCheckpoint(store))) {
      // optimization shortcut
      return true;
    }

    BeaconState state = checkpointStateStore.getState(checkpoint);
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(state);
    UInt64 honestFfgSupport = computeHonestFfgSupport(store, checkpoint, state);
    return honestFfgSupport.times(3).isGreaterThanOrEqualTo(totalActiveBalance);
  }

  // def will_checkpoint_be_justified(store: Store, checkpoint: Checkpoint) -> bool:
  private boolean willCurrentEpochCheckpointBeJustified(
      ReadOnlyStore store, Checkpoint checkpoint, CheckpointStateStore checkpointStateStore) {

    checkArgument(checkpoint.getEpoch().equals(getCurrentEpochStore(store)));

    BeaconState state = checkpointStateStore.getState(checkpoint);
    UInt64 totalActiveBalance = beaconStateAccessors.getTotalActiveBalance(state);
    UInt64 honestFfgSupport = computeHonestFfgSupport(store, checkpoint, state);
    return honestFfgSupport.times(3).isGreaterThanOrEqualTo(totalActiveBalance.times(2));
  }

  private boolean hasProtoarrayBlock(ReadOnlyStore store, Bytes32 blockRoot) {
    return store.getForkChoiceStrategy().blockSlot(blockRoot).isPresent();
  }

  private UInt64 getBlockEpoch(ReadOnlyStore store, Bytes32 blockRoot) {
    UInt64 blockSlot = store.getForkChoiceStrategy().blockSlot(blockRoot).orElseThrow();
    return miscHelpers.computeEpochAtSlot(blockSlot);
  }

  private UInt64 getCurrentEpochStore(ReadOnlyStore store) {
    UInt64 currentSlot = getCurrentSlot(store);
    return miscHelpers.computeEpochAtSlot(currentSlot);
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
    return forkChoiceStrategy.getAncestor(root, ancestorSlot).orElse(Bytes32.ZERO).equals(ancestor);
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
      CheckpointStateStore checkpointStateStore) {
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
            store, getCheckpointForBlock(store, headBlockRoot, currentEpoch), checkpointStateStore);
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
        if (!isOneConfirmed(store, blockRoot, checkpointStateStore)) {
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
          if (!willCurrentEpochCheckpointBeJustified(store, checkpoint, checkpointStateStore)) {
            break;
          }
        }
        // if not is_one_confirmed(store, block_root):
        //    break
        if (!isOneConfirmed(store, blockRoot, checkpointStateStore)) {
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
          || (!isTentativeVoutingSourceTooOld
              && (isFirstEpochSlot || willNoConflictingCheckpointBeJustified))) {
        // confirmed_root = tentative_confirmed_root
        confirmedRoot = tentativeConfirmedRoot;
      }
    }
    // return confirmed_root
    return confirmedRoot;
  }

  // def get_latest_confirmed(store: Store) -> Root:
  public Bytes32 getLatestConfirmed(
      ReadOnlyStore store,
      Bytes32 head /* TODO probably worth adding it to Store */,
      CheckpointStateStore checkpointStateStore) {
    //    confirmed_root = store.confirmed_root
    Bytes32 confirmedRoot = store.getConfirmedRoot();
    //    current_epoch = get_current_store_epoch(store)
    UInt64 currentEpoch = getCurrentEpochStore(store);

    //    # revert to finalized block if the latest confirmed block:
    //    # a) from two or more epochs ago
    //    # b) doesn't belong to the canonical chain
    //    # c) the confirmed chain starting from the previous epoch unrealized justified checkpoint
    //    #    cannot be re-confirmed at the beginning of the current epoch
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
    //    UInt64 confirmedBlockEpoch = getBlockEpochCached(store, confirmedRoot);
    boolean isFirstEpochSlot = isFirstEpochSlot(getCurrentSlot(store));
    boolean isNotReconfirmed =
        isFirstEpochSlot && !isChainReconfirmed(store, confirmedRoot, checkpointStateStore);
    if (!hasProtoarrayBlock(store, confirmedRoot)
        || getBlockEpoch(store, confirmedRoot).increment().isLessThan(currentEpoch)
        || !isAncestor(store, head, confirmedRoot)
        || isNotReconfirmed) {
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
    Optional<UInt64> prevUnrealizedJustifiedCeckpointSlot =
        store
            .getForkChoiceStrategy()
            .blockSlot(store.getPrevEpochUnrealizedJustifiedCheckpoint().getRoot());
    if (prevUnrealizedJustifiedCeckpointSlot.isEmpty()) {
      return confirmedRoot;
    }
    //    if (get_current_slot(store) % SLOTS_PER_EPOCH == 0
    //        and store.prev_slot_unrealized_justified_checkpoint.epoch + 1 == current_epoch
    //        and confirmed_block_slot < prev_unrealized_justified_checkpoint_slot):
    // FIX+ME (spec): isFirstEpochSlot
    UInt64 prevUnrealizedJustifiedCeckpointEpoch =
        store.getPrevEpochUnrealizedJustifiedCheckpoint().getEpoch();
    boolean isPrevUnrealizedJustifiedCeckpointTooOld =
        prevUnrealizedJustifiedCeckpointEpoch.increment().isLessThan(currentEpoch);
    boolean isNewConfirmedBlockGreater =
        confirmedBlockSlot.isLessThan(prevUnrealizedJustifiedCeckpointSlot.get());
    if (isFirstEpochSlot
        && !isPrevUnrealizedJustifiedCeckpointTooOld
        && isNewConfirmedBlockGreater) {
      //        confirmed_root = store.prev_slot_unrealized_justified_checkpoint.root
      confirmedRoot = store.getPrevEpochUnrealizedJustifiedCheckpoint().getRoot();
    }

    //    # attempt to further advance the latest confirmed block
    //    confirmed_block_epoch = compute_epoch_at_slot(store.blocks[confirmed_root].slot)
    UInt64 confirmedBlockEpoch = getBlockEpoch(store, confirmedRoot);
    //    if confirmed_block_epoch + 1 >= current_epoch:
    //        return find_latest_confirmed_descendant(store, confirmed_root)
    //    else:
    //        return confirmed_root
    if (confirmedBlockEpoch.increment().isGreaterThanOrEqualTo(currentEpoch)) {
      confirmedRoot =
          findLatestConfirmedDescendant(store, confirmedRoot, head, checkpointStateStore);
    }
    // put to cache
    return confirmedRoot;
  }
}
