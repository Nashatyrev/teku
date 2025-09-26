package tech.pegasys.teku.statetransition.forkchoice;

import org.apache.tuweni.bytes.Bytes32;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

class VoteTracker {

  record EpochVoter(UInt64 epoch, UInt64 voterIndex) {}

  private final Spec spec;
  private final AttestationStateSelector attestationStateSelector;

  final Map<Bytes32, Set<EpochVoter>> headBlockToVotes = new HashMap<>();
  final Map<UInt64, Set<EpochVoter>> slotToVotes = new HashMap<>();

  public VoteTracker(Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, new StubMetricsSystem());
  }

  int updateVotes(BeaconBlock block) {
    AttestationUtil attestationUtil = spec.atSlot(block.getSlot()).getAttestationUtil();
    int unseenVotersCount = 0;
    for (Attestation attestation :
        block.getBeaconBlock().orElseThrow().getBody().getAttestations()) {
      Bytes32 voteBlock = attestation.getData().getBeaconBlockRoot();
      BeaconState state = null;
      state =
          attestationStateSelector.getStateToValidate(attestation.getData()).join().orElseThrow();
      IndexedAttestation indexedAttestation =
          attestationUtil.getIndexedAttestation(state, attestation);

      UInt64 assignedEpoch = spec.computeEpochAtSlot(attestation.getData().getSlot());
      List<EpochVoter> voters =
          indexedAttestation
              .getAttestingIndices()
              .streamUnboxed()
              .map(valIdx -> new EpochVoter(assignedEpoch, valIdx))
              .toList();
      headBlockToVotes.computeIfAbsent(voteBlock, k -> new HashSet<>()).addAll(voters);
      Set<EpochVoter> votes =
          slotToVotes.computeIfAbsent(attestation.getData().getSlot(), k -> new HashSet<>());
      int oldVoteCount = votes.size();
      votes.addAll(voters);
      int newVoteCount = votes.size();
      unseenVotersCount += newVoteCount - oldVoteCount;
    }
    return unseenVotersCount;
  }

  public int getVoteCountForBlock(Bytes32 blockRoot) {
    return headBlockToVotes.getOrDefault(blockRoot, emptySet()).size();
  }

  public int getVoteCountInSlot(UInt64 slot) {
    return slotToVotes.getOrDefault(slot, emptySet()).size();
  }
}
