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

package tech.pegasys.teku.statetransition.forkchoice;

import static java.util.Collections.emptySet;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.statetransition.validation.AttestationStateSelector;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoteTracker {

  public record EpochVoter(UInt64 epoch, UInt64 voterIndex) {}

  private final Spec spec;
  private final AttestationStateSelector attestationStateSelector;

  final Map<Bytes32, Set<EpochVoter>> headBlockToVotes = LimitedMap.createNonSynchronized(100);
  final Map<UInt64, Set<EpochVoter>> slotToVotes = LimitedMap.createNonSynchronized(100);

  public VoteTracker(Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, new StubMetricsSystem());
  }

  Set<EpochVoter> updateVote(Attestation attestation) {
    AttestationUtil attestationUtil =
        spec.atSlot(attestation.getData().getSlot()).getAttestationUtil();
    Bytes32 voteBlock = attestation.getData().getBeaconBlockRoot();
    Optional<BeaconState> maybeState =
        attestationStateSelector.getStateToValidate(attestation.getData()).join();
    if (maybeState.isEmpty()) {
      // may happen when attestation for an uncle block
      return emptySet();
    }
    BeaconState state = maybeState.orElseThrow();
    IndexedAttestation indexedAttestation =
        attestationUtil.getIndexedAttestation(state, attestation);

    UInt64 assignedEpoch = spec.computeEpochAtSlot(attestation.getData().getSlot());
    Set<EpochVoter> votes =
        indexedAttestation
            .getAttestingIndices()
            .streamUnboxed()
            .map(valIdx -> new EpochVoter(assignedEpoch, valIdx))
            .collect(Collectors.toSet());
    headBlockToVotes.computeIfAbsent(voteBlock, k -> new HashSet<>()).addAll(votes);
    Set<EpochVoter> existingVotes =
        slotToVotes.computeIfAbsent(attestation.getData().getSlot(), k -> new HashSet<>());
    votes.removeAll(existingVotes);
    existingVotes.addAll(votes);
    return votes;
  }

  Set<EpochVoter> updateVotes(BeaconBlock block) {
    return updateVotes(block.getBeaconBlock().orElseThrow().getBody().getAttestations().asList());
  }

  Set<EpochVoter> updateVotes(Collection<Attestation> attestations) {
    return attestations.stream()
        .map(this::updateVote)
        .collect(HashSet::new, Set::addAll, Set::addAll);
  }

  public int getVoteCountForBlock(Bytes32 blockRoot) {
    return headBlockToVotes.getOrDefault(blockRoot, emptySet()).size();
  }

  public int getVoteCountInSlot(UInt64 slot) {
    return slotToVotes.getOrDefault(slot, emptySet()).size();
  }
}
