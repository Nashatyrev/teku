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

package tech.pegasys.teku.spec.generator;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.lang3.ObjectUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class SlashlessAttestationGenerator {

  private class EpochVotesData {
    final UInt64 epoch;
    final BitSet votedValidatorsBitset = new BitSet();
    final List<BitSet> slotVotedValidators;

    private EpochVotesData(UInt64 epoch) {
      this.epoch = epoch;
      this.slotVotedValidators = new ArrayList<>();
      for (int i = 0; i < spec.slotsPerEpoch(epoch); i++) {
        this.slotVotedValidators.add(new BitSet());
      }
    }

    boolean recordValidatorVote(UInt64 validatorIndex, UInt64 slot) {
      checkArgument(slot.isGreaterThanOrEqualTo(spec.computeStartSlotAtEpoch(epoch)));
      checkArgument(slot.isLessThan(spec.computeStartSlotAtEpoch(epoch.increment())));
      if (votedValidatorsBitset.get(validatorIndex.intValue())) {
        return false;
      }
      votedValidatorsBitset.set(validatorIndex.intValue());
      UInt64 slotInEpoch = slot.minus(spec.computeStartSlotAtEpoch(epoch));
      slotVotedValidators.get(slotInEpoch.intValue()).set(validatorIndex.intValue());
      return true;
    }
  }

  public record AttestationWithIndex(Attestation attestation, UInt64 validatorIndex) {}

  private final AttestationGenerator attestationGenerator;
  private final Spec spec;
  private final NavigableMap<UInt64, EpochVotesData> epochVotes = new TreeMap<>();

  public SlashlessAttestationGenerator(AttestationGenerator attestationGenerator, Spec spec) {
    this.attestationGenerator = attestationGenerator;
    this.spec = spec;
  }

  public AttestationStream newAttestationStream(
      StateAndBlockSummary headBlockAndState, final UInt64 assignedSlot) {
    AttestationUtil attestationUtil = spec.atSlot(assignedSlot).getAttestationUtil();

    return new AttestationStream(
        attestationGenerator
            .streamAttestations(headBlockAndState, assignedSlot)
            .map(
                att ->
                    new AttestationWithIndex(
                        att,
                        UInt64.valueOf(
                            attestationUtil
                                .getAttestingIndices(headBlockAndState.getState(), att)
                                .getInt(0))))
            .filter(a -> !isVoted(a)),
        assignedSlot);
  }

  public AttestationStream emptyAttestationStream() {
    return new AttestationStream(Stream.empty(), UInt64.MAX_VALUE);
  }

  private EpochVotesData getOrCreateEpochVotesData(UInt64 epoch) {
    return epochVotes.computeIfAbsent(epoch, e -> new EpochVotesData(epoch));
  }

  private UInt64 getAttestationEpoch(Attestation attestation) {
    return spec.computeEpochAtSlot(attestation.getData().getSlot());
  }

  /**
   * @return true if validator has not voted in the epoch yet
   */
  private boolean recordVote(AttestationWithIndex attWithIdx) {
    if (isVoted(attWithIdx)) {
      return false;
    }
    EpochVotesData epochVotesData =
        getOrCreateEpochVotesData(getAttestationEpoch(attWithIdx.attestation()));
    return epochVotesData.recordValidatorVote(
        attWithIdx.validatorIndex(), attWithIdx.attestation().getData().getSlot());
  }

  private boolean isVoted(AttestationWithIndex attWithIdx) {
    EpochVotesData epochVotesData =
        getOrCreateEpochVotesData(getAttestationEpoch(attWithIdx.attestation()));
    return epochVotesData.votedValidatorsBitset.get(attWithIdx.validatorIndex().intValue());
  }

  public void prune(UInt64 epoch) {
    epochVotes.headMap(epoch, true).clear();
  }

  public class AttestationStream {
    private final Stream<AttestationWithIndex> attestations;
    private final UInt64 earliesEpoch;

    private AttestationStream(Stream<AttestationWithIndex> attestations, UInt64 earliesEpoch) {
      this.attestations = attestations;
      this.earliesEpoch = earliesEpoch;
    }

    private AttestationStream transform(
        Function<Stream<AttestationWithIndex>, Stream<AttestationWithIndex>> streamTransformer) {
      return new AttestationStream(streamTransformer.apply(attestations), this.earliesEpoch);
    }

    public AttestationStream filter(Predicate<AttestationWithIndex> predicate) {
      return transform(s -> s.filter(ai -> predicate.test(ai)));
    }

    public AttestationStream limit(int limit) {
      return transform(s -> s.limit(limit));
    }

    public AttestationStream concat(AttestationStream other) {
      UInt64 minSlot = ObjectUtils.min(this.earliesEpoch, other.earliesEpoch);
      return new AttestationStream(Stream.concat(attestations, other.attestations), minSlot);
    }

    public AttestationStream takeAndDrop(int count) {
      int[] dropCounter = new int[1];
      return transform(s -> s.filter(ai -> {
        dropCounter[0]++;
        if (dropCounter[0] <= count) {
          recordVote(ai);
          return false;
        } else {
          return true;
        }
      } ));
    }

    public List<Attestation> takeAll() {
      return attestations
          .filter(SlashlessAttestationGenerator.this::recordVote)
          .map(AttestationWithIndex::attestation)
          .toList();
    }

    public List<Attestation> takeAllAggregated() {
      return takeAggregatedLimited(Integer.MAX_VALUE);
    }

    public List<Attestation> takeAggregatedLimitedForBlock() {
      int maxAttestations = spec.getSpecConfig(earliesEpoch).getMaxAttestations();
      return takeAggregatedLimited(maxAttestations);
    }

    private List<Attestation> takeAggregatedLimited(int limit) {
      Map<AttestationData, List<Attestation>> groupedAttestations = new HashMap<>();
      Iterator<AttestationWithIndex> it = attestations.iterator();
      while (it.hasNext()) {
        AttestationWithIndex attWithIdx = it.next();
        List<Attestation> aggregationList =
            groupedAttestations.get(attWithIdx.attestation().getData());
        if (aggregationList == null) {
          if (groupedAttestations.size() == limit) {
            break;
          } else {
            aggregationList = new ArrayList<>();
            groupedAttestations.put(attWithIdx.attestation().getData(), aggregationList);
          }
        }
        if (recordVote(attWithIdx)) {
          aggregationList.add(attWithIdx.attestation());
        }
      }
      return groupedAttestations.values().stream()
          .map(AttestationGenerator::aggregateAttestations)
          .toList();
    }
  }
}
