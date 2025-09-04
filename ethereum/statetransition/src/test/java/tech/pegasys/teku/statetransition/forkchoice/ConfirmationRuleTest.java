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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
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
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
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
  private DataStructureUtil dataStructureUtil;
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
      mock(AvailabilityChecker.class);

  private AttestationSchema<?> attestationSchema;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private SignedBlockAndState genesis;
  private RecentChainData recentChainData;

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

  private static final int COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR = 5;
  private static final int CONFIRMATION_BYZANTINE_THRESHOLD = 29;
  private static final int CONFIRMATION_SLASHING_THRESHOLD = 33;

  @BeforeEach
  public void setup() {
    setupWithSpec(
        TestSpecFactory.createMinimalBellatrix(
            builder ->
                builder
                    .committeeWeightEstimationAdjustmentFactor(
                        COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR)
                    .confirmationByzantineThreshold(CONFIRMATION_BYZANTINE_THRESHOLD)
                    .confirmationSlashingThreshold(CONFIRMATION_SLASHING_THRESHOLD)));
  }

  private void setupWithSpec(final Spec unmockedSpec) {
    // Setting up spec and all dependants
    this.spec = spy(unmockedSpec);
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.attestationSchema = spec.getGenesisSchemaDefinitions().getAttestationSchema();
    this.storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .specProvider(spec)
            .numberOfValidators(16)
            .build();
    this.chainBuilder = storageSystem.chainBuilder();
    this.genesis = chainBuilder.generateGenesis();
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

  private SignedBeaconBlock importNextBlockWithAllAttestations() {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    UInt64 headSlot = chainUpdater.getHeadSlot();
    return importNextBlockWithAllAttestations(headSlot);
  }

  private SignedBeaconBlock importNextBlockWithAllAttestations(UInt64 headSlot) {
    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    final UInt64 newBlockSlot = headSlot.increment();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(newBlockSlot)
        .forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        chainBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);
    importBlock(epoch2Block);
    return epoch2Block.getBlock();
  }

  @Test
  void sanityTest() {
    ArrayList<BeaconBlock> allBlocks = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      SignedBeaconBlock block = importNextBlockWithAllAttestations();
      allBlocks.add(block.getMessage());
    }

    assertThat(allBlocks.get(allBlocks.size() - 2).getRoot())
        .isEqualTo(storageSystem.recentChainData().getStore().getConfirmedRoot());
  }

  @Test
  void testSeveralEpochsEmptySlotGap() {
    UpdatableStore store = storageSystem.recentChainData().getStore();
    ArrayList<BeaconBlock> allBlocks = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      SignedBeaconBlock block = importNextBlockWithAllAttestations();
      allBlocks.add(block.getMessage());
    }

    assertThat(store.getConfirmedRoot()).isEqualTo(allBlocks.get(allBlocks.size() - 2).getRoot());

    UInt64 slotAfterGap = allBlocks.getLast().getSlot().plus(8 * 3);

    SignedBeaconBlock gapBlock = importNextBlockWithAllAttestations(slotAfterGap);
    allBlocks.add(gapBlock.getMessage());

    // rollback to finalized
    Bytes32 finalizedRoot = store.getFinalizedCheckpoint().getRoot();
    // don't think this is right
    assertThat(store.getConfirmedRoot()).isEqualTo(finalizedRoot);

    for (int i = 0; i < 64; i++) {
      SignedBeaconBlock block = importNextBlockWithAllAttestations();
      allBlocks.add(block.getMessage());
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
    ConfirmationRuleUtil confirmationRuleUtil = spec.getGenesisSpec().getConfirmationRuleUtil();
    UInt64 checkpointWeight =
        confirmationRuleUtil.getCheckpointWeight(
            recentChainData.getStore(), genesisCheckpoint, state);
    UInt64 validatorBalance = EthConstants.ETH_TO_GWEI.times(32);

    // 2 of 3 votes are equivocating, but should still count
    assertThat(checkpointWeight).isEqualTo(validatorBalance.times(3));
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
