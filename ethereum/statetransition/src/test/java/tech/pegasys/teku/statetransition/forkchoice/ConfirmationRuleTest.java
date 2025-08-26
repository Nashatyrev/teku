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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoice.BLOCK_CREATION_TOLERANCE_MS;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.mockito.verification.VerificationMode;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

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

  @BeforeEach
  public void setup() {
    setupWithSpec(TestSpecFactory.createMinimalBellatrix());
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

  private void importNextBlockWithAllAttestations() {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    UInt64 headSlot = chainUpdater.getHeadSlot();

    // Add a block with enough attestations to justify the epoch.
    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    final UInt64 newBlockSlot = headSlot.increment();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(newBlockSlot)
        .forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        chainBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);
    importBlock(epoch2Block);
  }

  @Test
  void sanityTest() {
    doMerge();

    for(int i = 0; i < 64; i++) {
      importNextBlockWithAllAttestations();
    }

//    // since protoArray initializes with optimistic nodes,
//    // we expect a first notification to be optimistic false
//    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(false);
//
//    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
//    importBlock(epoch4Block);
//
//    slotToImport = prepFinalizeEpoch(4);
//
//    // make EL returning SYNCING
//    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
//
//    // generate block which finalize epoch 4
//    final SignedBlockAndState epoch6Block = chainBuilder.generateBlockAtSlot(slotToImport);
//    importBlockOptimistically(epoch6Block);
//
//    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(true);
//
//    UInt64 forkSlot = slotToImport.increment();
//
//    storageSystem.chainUpdater().setCurrentSlot(forkSlot);
//
//    ChainBuilder alternativeChain = chainBuilder.fork();
//
//    // make EL returning SYNCING
//    executionLayer.setPayloadStatus(PayloadStatus.VALID);
//
//    importBlock(chainBuilder.generateBlockAtSlot(forkSlot));
//
//    verify(optimisticSyncStateTracker, times(2)).onOptimisticHeadChanged(false);
//
//    // builds atop the canonical chain
//    storageSystem.chainUpdater().setCurrentSlot(forkSlot.plus(1));
//    importBlock(chainBuilder.generateBlockAtSlot(forkSlot.plus(1)));
//
//    processHead(forkSlot.plus(1));
//
//    // make EL returning SYNCING
//    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
//
//    // import a fork which won't be canonical
//    importBlockOptimistically(alternativeChain.generateBlockAtSlot(forkSlot));
//
//    // no notification is expected
//    verifyNoMoreInteractions(optimisticSyncStateTracker);
  }

  private void assertForkChoiceUpdateNotification(
      final SignedBlockAndState blockAndState,
      final boolean optimisticHead,
      final VerificationMode mode) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final UInt64 headExecutionBlockNumber =
        forkChoiceStrategy.executionBlockNumber(blockAndState.getRoot()).orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(blockAndState.getRoot()).orElseThrow();
    final Bytes32 justifiedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getJustifiedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    verify(forkChoiceNotifier, mode)
        .onForkChoiceUpdated(
            new ForkChoiceState(
                blockAndState.getRoot(),
                blockAndState.getSlot(),
                headExecutionBlockNumber,
                headExecutionHash,
                justifiedExecutionHash,
                finalizedExecutionHash,
                optimisticHead),
            Optional.empty());
  }

  private void justifyEpoch(final ChainUpdater chainUpdater, final long epoch) {
    final UInt64 nextEpochStartSlot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch + 1))
            .max(chainUpdater.getHeadSlot().plus(1));
    // Advance chain to an epoch we can actually justify
    prepEpochForJustification(chainUpdater, nextEpochStartSlot);

    // Trigger epoch transition into next epoch.
    importBlock(chainBuilder.generateBlockAtSlot(nextEpochStartSlot));

    // Should now have justified epoch
    assertThat(recentChainData.getJustifiedCheckpoint())
        .map(Checkpoint::getEpoch)
        .contains(UInt64.valueOf(epoch));
  }

  private void prepEpochForJustification(
      final ChainUpdater chainUpdater, final UInt64 nextEpochStartSlot) {
    UInt64 headSlot = chainUpdater.getHeadSlot();
    final UInt64 targetHeadSlot = nextEpochStartSlot.minus(2);
    while (headSlot.isLessThan(targetHeadSlot)) {
      SignedBlockAndState headBlock = chainBuilder.generateBlockAtSlot(headSlot.plus(1));
      importBlock(headBlock);
      assertThat(recentChainData.getBestBlockRoot()).contains(headBlock.getRoot());
      headSlot = headBlock.getSlot();
    }

    // Add a block with enough attestations to justify the epoch.
    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    final UInt64 newBlockSlot = headSlot.increment();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(newBlockSlot)
        .forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        chainBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);

    importBlock(epoch2Block);
  }

  private UInt64 prepFinalizeEpoch(final long epoch) {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final UInt64 epochPlus2StartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch).plus(2));

    justifyEpoch(chainUpdater, epoch);

    prepEpochForJustification(chainUpdater, epochPlus2StartSlot);

    return epochPlus2StartSlot;
  }

  private void finalizeEpoch(final long epoch) {
    final UInt64 nextBlockSlot = prepFinalizeEpoch(epoch);
    importBlock(chainBuilder.generateBlockAtSlot(nextBlockSlot));
  }

  private void doMerge() {
    doMerge(false);
  }

  private void doMerge(final boolean optimistic) {
    final SignedBlockAndState epoch4Block = generateMergeBlock();

    if (optimistic) {
      storageSystem.chainUpdater().saveOptimisticBlock(epoch4Block);
    } else {
      storageSystem.chainUpdater().saveBlock(epoch4Block);
    }
    storageSystem.chainUpdater().updateBestBlock(epoch4Block);
  }

  private SignedBlockAndState generateMergeBlock() {
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 terminalBlockParentHash = dataStructureUtil.randomBytes32();
    final PowBlock terminalBlock = new PowBlock(terminalBlockHash, terminalBlockParentHash, ZERO);
    final PowBlock terminalParentBlock =
        new PowBlock(terminalBlockParentHash, dataStructureUtil.randomBytes32(), ZERO);
    executionLayer.addPowBlock(terminalBlock);
    executionLayer.addPowBlock(terminalParentBlock);
    return chainBuilder.generateBlockAtSlot(
        storageSystem.chainUpdater().getHeadSlot().plus(1),
        BlockOptions.create().setTerminalBlockHash(terminalBlockHash));
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

  private void importBlockOptimistically(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportedSuccessfully(result, true);
  }

  private void assertBlockImportFailure(
      final SafeFuture<BlockImportResult> importResult, final FailureReason failureReason) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = safeJoin(importResult);
    assertThat(result.getFailureReason()).isEqualTo(failureReason);
  }

  private void importBlockAndAssertFailure(
      final SignedBlockAndState block, final FailureReason failureReason) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportFailure(result, failureReason);
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
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
}
