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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.generator.SlashlessAttestationGenerator;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.ConfirmationRuleUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

class ConfirmationRuleTest {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private Spec spec;
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
      mock(AvailabilityChecker.class);

  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private SignedBlockAndState genesis;
  private RecentChainData recentChainData;
  private ConfirmationRuleUtil confirmationRuleUtil;

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final OptimisticHeadSubscriber optimisticSyncStateTracker =
      mock(OptimisticHeadSubscriber.class);
  private ExecutionLayerChannelStub executionLayer;
  private final BlockBroadcastValidator blockBroadcastValidator =
      mock(BlockBroadcastValidator.class);
  private final MergeTransitionBlockValidator transitionBlockValidator =
      mock(MergeTransitionBlockValidator.class);
  private final DebugDataDumper debugDataDumper = mock(DebugDataDumper.class);

  private final InlineEventThread eventThread = new InlineEventThread();

  private ForkChoice forkChoice;
  private VoteTracker voteTracker;
  ArrayList<BeaconBlock> allBlocks = new ArrayList<>();

  private static final UInt64 validatorBalance = EthConstants.ETH_TO_GWEI.times(32);
  //  private static final int VALIDATOR_COUNT = 1_000_000;
    private static final int VALIDATOR_COUNT = 1 << 13;
//  private static final int VALIDATOR_COUNT = 1024;
  private static final int COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR = 5;
  private static final int CONFIRMATION_BYZANTINE_THRESHOLD = 25;
  private static final int CONFIRMATION_SLASHING_THRESHOLD = 25;

  @BeforeEach
  public void setup() {
    BLSConstants.disableBLSVerification();
    setupWithSpec(
        TestSpecFactory.createMainnetBellatrix(
            //        TestSpecFactory.createMinimalBellatrix(
            builder ->
                builder
                    .committeeWeightEstimationAdjustmentFactor(
                        COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR)
                    .confirmationByzantineThreshold(CONFIRMATION_BYZANTINE_THRESHOLD)
                    .confirmationSlashingThreshold(CONFIRMATION_SLASHING_THRESHOLD)));
    voteTracker = new VoteTracker(spec, storageSystem.recentChainData());
  }

  private void setupWithSpec(final Spec unmockedSpec) {
    // Setting up spec and all dependants
    this.spec = unmockedSpec; // spy(unmockedSpec);
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    this.storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .specProvider(spec)
            .numberOfValidators(VALIDATOR_COUNT)
            .build();
    this.chainBuilder = storageSystem.chainBuilder();
    this.genesis = chainBuilder.generateGenesis(UInt64.ZERO, false);
    this.recentChainData = storageSystem.recentChainData();
    this.executionLayer = new ExecutionLayerChannelStub(spec, false);
    this.forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            blobSidecarManager,
            DasSamplerManager.NOOP,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            debugDataDumper,
            metricsSystem);
    this.confirmationRuleUtil = spec.getGenesisSpec().getConfirmationRuleUtil();

    // Starting and mocks
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    reset(
        forkChoiceNotifier,
        transitionBlockValidator); // Clear any notifications from setting genesis

    // by default everything is valid
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.SUCCESS));

    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(optimisticSyncStateTracker);

    // blobs always available
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      when(blobSidecarManager.createAvailabilityChecker(any()))
          .thenReturn(blobSidecarsAvailabilityChecker);
      final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecars(2);
      when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
          .thenReturn(
              SafeFuture.completedFuture(DataAndValidationResult.validResult(blobSidecars)));
    } else {
      when(blobSidecarManager.createAvailabilityChecker(any()))
          .thenReturn(AvailabilityChecker.NOOP_BLOBSIDECAR);
    }
  }

  private SignedBlockAndState importNextBlockWithAllAttestations() {
    return importNextBlockWithAllAttestations(chainBuilder);
  }

  private SignedBlockAndState importNextBlockWithAllAttestations(ChainBuilder forkBuilder) {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    UInt64 headSlot = chainUpdater.getHeadSlot();
    return importNextBlockWithAllAttestations(headSlot, forkBuilder);
  }

  private SignedBlockAndState importNextBlockWithAllAttestations(UInt64 headSlot) {
    return importNextBlockWithAllAttestations(headSlot, chainBuilder);
  }

  private SignedBlockAndState importNextBlockWithAllAttestations(
      UInt64 headSlot, ChainBuilder forkBuilder) {
    return importNextBlockWithPartialAttestations(headSlot, forkBuilder, 100);
  }

  private SignedBlockAndState importNextBlockWithPartialAttestations(
      int participationRatePercents) {
    return importNextBlockWithPartialAttestations(
        storageSystem.chainUpdater().getHeadSlot(), chainBuilder, participationRatePercents);
  }

  private SignedBlockAndState importNextBlockWithPartialAttestations(
      UInt64 headSlot, ChainBuilder forkBuilder, int participationRatePercents) {
    final UInt64 newBlockSlot = headSlot.increment();
    List<Attestation> blockAggregates =
        forkBuilder.takeValidAggregatedAttestationsForBlockAtSlot(
            newBlockSlot, participationRatePercents);
    return importNextBlockWithAttestations(headSlot, blockAggregates, forkBuilder);
  }

  private SignedBlockAndState importNextBlockWithAttestations(
      UInt64 headSlot, List<Attestation> attestations) {
    return importNextBlockWithAttestations(headSlot, attestations, chainBuilder);
  }

  private SignedBlockAndState importNextBlockWithAttestations(
      UInt64 headSlot, List<Attestation> atts, ChainBuilder forkBuilder) {
    final UInt64 newBlockSlot = headSlot.increment();

    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    atts.forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        forkBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);
    importBlock(epoch2Block);
    return epoch2Block;
  }

  @Test
  void sanityTest() {
    for (int i = 0; i < 64; i++) {
      importNextBlockWithAllAttestations();
    }

    for (int i = 0; i < 64; i++) {
      importNextBlockWithAllAttestations();
      assertThat(allBlocks.get(allBlocks.size() - 2).getRoot())
          .isEqualTo(storageSystem.recentChainData().getStore().getConfirmedRoot());
    }
  }

  @Test
  void testSeveralEpochsEmptySlotGap() {
    UpdatableStore store = storageSystem.recentChainData().getStore();
    for (int i = 1; i < 64; i++) {
      importNextBlockWithAllAttestations();
    }
    SignedBlockAndState b64 = importNextBlockWithAllAttestations();

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());

    UInt64 slotAfterGap = allBlocks.getLast().getSlot().plus(62);
    // drain all attestations - it was black out after all
    for (int slot = 64; slot < slotAfterGap.intValue(); slot++) {
      chainBuilder
          .getAttestationGenerator()
          .newAttestationStream(b64, UInt64.valueOf(slot))
          .takeAll();
    }

    SignedBeaconBlock b127 = importNextBlockWithAllAttestations(slotAfterGap).getBlock();

    // rollback to finalized
    Bytes32 finalizedRoot = store.getFinalizedCheckpoint().getRoot();
    assertThat(store.getConfirmedRoot()).isEqualTo(finalizedRoot);

    SignedBeaconBlock b128 = importNextBlockWithAllAttestations().getBlock();

    for (int i = 0; i < 32 * 2; i++) {
      importNextBlockWithAllAttestations();
    }

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());
  }

  @Test
  void testOneEmptySlotGap() {
    int participationRatePercent = 100;

    UpdatableStore store = storageSystem.recentChainData().getStore();
    for (int i = 0; i < 16; i++) {
      importNextBlockWithPartialAttestations(participationRatePercent);
    }

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());

    UInt64 slotAfterGap = allBlocks.getLast().getSlot().plus(1);

    importNextBlockWithPartialAttestations(slotAfterGap, chainBuilder, participationRatePercent);

    for (int i = 0; i < 16; i++) {
      importNextBlockWithPartialAttestations(participationRatePercent);
    }

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 95, 90, 85, 80, 75, 70, 60, 50})
  void testParticipationRate(int participationRatePercent) {
    for (int i = 0; i < 64; i++) {
      importNextBlockWithPartialAttestations(participationRatePercent);
    }
  }


    @ParameterizedTest
  @ValueSource(ints = {10, 15, 20, 30, 35, 40, 50, 60, 70, 90, 100})
  void testFirstEpochBlockAttestationDeficit(int blockAttestationDeficitPercent) {
    UpdatableStore store = storageSystem.recentChainData().getStore();
    for (int i = 1; i < 63; i++) { // run 2 epochs to 'warm up' justification processing
      importNextBlockWithAllAttestations();
    }

    SignedBlockAndState block63 = importNextBlockWithAllAttestations();
    SignedBlockAndState weakBlock64 = importNextBlockWithAllAttestations();

    int votesPerSlotCount = VALIDATOR_COUNT / spec.getGenesisSpecConfig().getSlotsPerEpoch();
    int votesForWeakBlockCount =
        votesPerSlotCount - votesPerSlotCount * blockAttestationDeficitPercent / 100;
    SlashlessAttestationGenerator.AttestationStream votesForWeakBlock =
        chainBuilder
            .getAttestationGenerator()
            .newAttestationStream(weakBlock64, UInt64.valueOf(64))
            .limit(votesForWeakBlockCount);
    SlashlessAttestationGenerator.AttestationStream remainingVotesForBlock63 =
        chainBuilder.getAttestationGenerator().newAttestationStream(block63, UInt64.valueOf(64));
    List<Attestation> block65Atts =
        votesForWeakBlock.concat(remainingVotesForBlock63).takeAggregatedLimitedForBlock();

    importNextBlockWithAttestations(UInt64.valueOf(64), block65Atts);

    // max 6 blocks to recover
    for (int i = 0; i < 6; i++) {
      importNextBlockWithAllAttestations();
    }

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());
  }

  @Test
  void getCheckpointWeight_shouldCountEquivocatingVotesAndShouldntCountProposerBoost() {
    final ChainBuilder forkChain = chainBuilder.fork();
    final SignedBlockAndState forkBlock =
        forkChain.generateNextBlock(
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    // eventually better chain with an empty block
    final SignedBlockAndState betterBlock = chainBuilder.generateNextBlock(1);

    importBlock(forkBlock);

    // Add an attestation for the fork so that it initially has higher weight
    // Otherwise ties are split based on the hash which is too hard to control in the test
    final BlockOptions forkBlockOptions = BlockOptions.create();
    final List<Attestation> forkAttestations =
        forkChain.streamValidAttestationsWithTargetBlock(forkBlock).limit(2).toList();
    forkAttestations.forEach(forkBlockOptions::addAttestation);
    final SignedBlockAndState forkBlock1 = forkChain.generateNextBlock(forkBlockOptions);
    importBlock(forkBlock1);

    // Now import what will become the canonical chain
    importBlock(betterBlock);

    // Import a block with one attestation on what will be better chain
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsWithTargetBlock(betterBlock)
        .limit(1)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations = chainBuilder.generateNextBlock(options);
    importBlock(blockWithAttestations);

    // Add 2 AttesterSlashing on betterBlock chain, so it will become finally better
    final BlockOptions options2 = BlockOptions.create();
    forkAttestations.forEach(
        attestation ->
            options2.addAttesterSlashing(
                chainBuilder.createAttesterSlashingForAttestation(attestation, forkBlock)));
    final SignedBlockAndState blockWithAttesterSlashings = chainBuilder.generateNextBlock(options2);
    importBlock(blockWithAttesterSlashings);

    processHead(blockWithAttesterSlashings.getSlot());

    Checkpoint genesisCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    BeaconState state =
        recentChainData.retrieveCheckpointState(genesisCheckpoint).join().orElseThrow();
    UInt64 checkpointWeight =
        confirmationRuleUtil.getCheckpointWeight(
            recentChainData.getStore(), genesisCheckpoint, state);

    // 2 of 3 votes are equivocating, but should still count
    assertThat(checkpointWeight).isEqualTo(validatorBalance.times(3));
  }

  @Test
  void getCheckpointWeight_shouldCountVotesOnlyInTheCheckpointEpoch() {
    int slotsPerEpoch = spec.slotsPerEpoch(UInt64.ZERO);
    final BlockOptions options = BlockOptions.create();
    // 1 attestations for epoch 0
    chainBuilder
        .getAttestationGenerator()
        .newAttestationStream(genesis, UInt64.valueOf(slotsPerEpoch - 1))
        .limit(1)
        .takeAggregatedLimitedForBlock()
        .forEach(options::addAttestation);
    // 2 attestations for epoch 1
    chainBuilder
        .getAttestationGenerator()
        .newAttestationStream(genesis, UInt64.valueOf(slotsPerEpoch))
        .limit(2)
        .takeAggregatedLimitedForBlock()
        .forEach(options::addAttestation);

    final SignedBlockAndState epoch1Block =
        chainBuilder.generateNextBlock(slotsPerEpoch + 1, options);

    importBlock(epoch1Block);

    Checkpoint epoch1Checkpoint = new Checkpoint(UInt64.ONE, genesis.getRoot());
    UInt64 checkpointWeight =
        confirmationRuleUtil.getCheckpointWeight(
            recentChainData.getStore(), epoch1Checkpoint, genesis.getState());

    assertThat(checkpointWeight).isEqualTo(validatorBalance.times(2));
  }

  @Test
  void testShortReorgWhenForkBlockIsTheFirstInEpoch() {
    UpdatableStore store = storageSystem.recentChainData().getStore();
    for (int i = 1; i < 63; i++) { // run 2 epochs to 'warm up' justification processing
      importNextBlockWithAllAttestations();
    }

    // #63
    importNextBlockWithAllAttestations();

    ChainBuilder forkB = chainBuilder.fork();

    // #64
    importNextBlockWithAllAttestations(forkB);
    // #65
    importNextBlockWithAllAttestations(forkB);

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());
    // #64 is now confirmed, let's reorg it

    importNextBlockWithAllAttestations(UInt64.valueOf(65));
    importNextBlockWithAllAttestations(UInt64.valueOf(66));
    importNextBlockWithAllAttestations(UInt64.valueOf(67));
    SignedBeaconBlock b69a = importNextBlockWithAllAttestations(UInt64.valueOf(68)).getBlock();

    // check the reorg happened
    assertThat(storageSystem.getChainHead().getRoot()).isEqualTo(b69a.getRoot());
    // check that confirmed block is reset to finalized after being reorged
    Bytes32 finalizedRoot = store.getFinalizedCheckpoint().getRoot();
    assertThat(store.getConfirmedRoot()).isEqualTo(finalizedRoot);
  }

  @Test
  void testShortReorgWhenForkBlockInMiddleOfEpoch() {
    UpdatableStore store = storageSystem.recentChainData().getStore();
    for (int i = 1; i < 66; i++) { // run 2 epochs to 'warm up' justification processing
      importNextBlockWithAllAttestations();
    }

    // #66
    SignedBeaconBlock b66 = importNextBlockWithAllAttestations().getBlock();

    ChainBuilder forkB = chainBuilder.fork();

    // #67 fork B
    SignedBeaconBlock b67b = importNextBlockWithAllAttestations(forkB).getBlock();
    // #68 fork B
    importNextBlockWithAllAttestations(forkB);

    assertThat(store.getConfirmedRoot()).isEqualTo(b67b.getRoot());
    // #67 is now confirmed, let's reorg it

    // #69
    importNextBlockWithAllAttestations(UInt64.valueOf(68));
    // #70
    importNextBlockWithAllAttestations(UInt64.valueOf(69));
    // #71
    importNextBlockWithAllAttestations(UInt64.valueOf(70));
    // #72
    SignedBeaconBlock b72a = importNextBlockWithAllAttestations(UInt64.valueOf(71)).getBlock();

    // check the reorg happened
    assertThat(storageSystem.getChainHead().getRoot()).isEqualTo(b72a.getRoot());
    // check that confirmed block is reset to finalized after being reorged
    Bytes32 finalizedRoot = store.getFinalizedCheckpoint().getRoot();
    assertThat(store.getConfirmedRoot()).isEqualTo(finalizedRoot);
  }

  private void assertBlockImportedSuccessfully(
      final SafeFuture<BlockImportResult> importResult, final boolean optimistically) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = safeJoin(importResult);
    assertThat(result.isSuccessful()).describedAs(result.toString()).isTrue();
    assertThat(result.isImportedOptimistically())
        .describedAs(result.toString())
        .isEqualTo(optimistically);
  }

  private void importBlock(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportedSuccessfully(result, false);
    forkChoice.processHead();
    trackBlockAndVotes(block.getBeaconBlock().orElseThrow());
  }

  private void trackBlockAndVotes(BeaconBlock block) {
    allBlocks.add(block);
    int newVotesInBlock = voteTracker.updateVotes(block.getBeaconBlock().orElseThrow());
    System.err.println(
        "Importing block: "
            + block.getSlot()
            + ", "
            + block.getRoot().toString().substring(0, 8)
            + " ~> "
            + block.getParentRoot().toString().substring(0, 8)
            + ", votes in block: "
            + newVotesInBlock);

    int blocksPrinted = 0;
    for (int slot = block.getSlot().intValue() - 1; slot > 0; slot--) {
      if (blocksPrinted == 3) {
        break;
      }

      Optional<SignedBeaconBlock> mbSlotBlock =
          storageSystem.combinedChainDataClient().getBlockAtSlotExact(UInt64.valueOf(slot)).join();
      int voteCountInSlot = voteTracker.getVoteCountInSlot(UInt64.valueOf(slot));
      if (mbSlotBlock.isPresent()) {
        SignedBeaconBlock slotBlock = mbSlotBlock.get();
        System.err.println(
            "\tSlot/block votes/slot votes:\t"
                + slot
                + "\t"
                + voteTracker.getVoteCountForBlock(slotBlock.getRoot())
                + "\t"
                + voteCountInSlot);
        blocksPrinted++;
      } else {
        if (voteCountInSlot > 0) {
          System.err.println("\tSlot             slot votes:\t" + slot + "\t\t" + voteCountInSlot);
        }
      }
    }
  }

  private void setForkChoiceNotifierForkChoiceUpdatedResult(final PayloadStatus status) {
    setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(List.of(status));
  }

  private void setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(
      final List<PayloadStatus> statuses) {
    if (statuses.isEmpty()) {
      return;
    }
    Stubber stubber = null;
    for (PayloadStatus status : statuses) {
      Optional<ForkChoiceUpdatedResult> result =
          Optional.ofNullable(status)
              .map(payloadStatus -> new ForkChoiceUpdatedResult(payloadStatus, Optional.empty()));
      Answer<Void> onForkChoiceUpdatedResultAnswer = getOnForkChoiceUpdatedResultAnswer(result);
      if (stubber == null) {
        stubber = doAnswer(onForkChoiceUpdatedResultAnswer);
      } else {
        stubber.doAnswer(onForkChoiceUpdatedResultAnswer);
      }
    }
    stubber.when(forkChoiceNotifier).onForkChoiceUpdated(any(), any());
  }

  private Answer<Void> getOnForkChoiceUpdatedResultAnswer(
      final Optional<ForkChoiceUpdatedResult> result) {
    return invocation -> {
      result.ifPresent(
          forkChoiceUpdatedResult ->
              forkChoice.onForkChoiceUpdatedResult(
                  new ForkChoiceUpdatedResultNotification(
                      invocation.getArgument(0),
                      Optional.empty(),
                      false,
                      SafeFuture.completedFuture(forkChoiceUpdatedResult))));
      return null;
    };
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
  }
}
