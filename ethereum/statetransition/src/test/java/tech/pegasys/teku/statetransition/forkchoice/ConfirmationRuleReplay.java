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
import static org.mockito.Mockito.withSettings;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.forkchoice.replay.BeaconCache;
import tech.pegasys.teku.statetransition.forkchoice.replay.XatuConnector;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@Disabled("For manual running only")
class ConfirmationRuleReplay {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final BlobSidecarManager blobSidecarManager =
      mock(BlobSidecarManager.class, withSettings().stubOnly());

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
      mock(AvailabilityChecker.class, withSettings().stubOnly());

  private StorageSystem storageSystem;
  private RecentChainData recentChainData;

  private final ForkChoiceNotifier forkChoiceNotifier =
      mock(ForkChoiceNotifier.class, withSettings().stubOnly());
  private final OptimisticHeadSubscriber optimisticSyncStateTracker =
      mock(OptimisticHeadSubscriber.class, withSettings().stubOnly());
  private ExecutionLayerChannelStub executionLayer;
  private final BlockBroadcastValidator blockBroadcastValidator =
      mock(BlockBroadcastValidator.class, withSettings().stubOnly());
  private final MergeTransitionBlockValidator transitionBlockValidator =
      mock(MergeTransitionBlockValidator.class, withSettings().stubOnly());
  private final DebugDataDumper debugDataDumper =
      mock(DebugDataDumper.class, withSettings().stubOnly());

  private final InlineEventThread eventThread = new InlineEventThread();

  private ForkChoice forkChoice;

  private static final int COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR = 5;
  private static final int CONFIRMATION_BYZANTINE_THRESHOLD = 25;
  private static final int CONFIRMATION_SLASHING_THRESHOLD = 25;

  String jsonApiEndpoint =
      "https://crimson-proud-shard.quiknode.pro/51b0281db82fc937c36866e6a80ee6235e2f7f3a";
  //
  // "https://beaconstate.ethstaker.cc/eth/v2/debug/beacon/states/0x8732754dbbc6391165ff2047f0aa543d08dde6bfbf701c653a6476472201e178";
  //
  // "https://teku-beacon-archive-prod.staking.consensys.io/eth/v2/debug/beacon/states/0x8732754dbbc6391165ff2047f0aa543d08dde6bfbf701c653a6476472201e178";
  //      "https://beaconstate.ethstaker.cc/eth/v2/debug/beacon/states/finalized";
  String statePath = "/eth/v2/debug/beacon/states/";
  String blockPath = "/eth/v2/beacon/blocks/";
  //  String anchorStateRoot = "0x8732754dbbc6391165ff2047f0aa543d08dde6bfbf701c653a6476472201e178";
  //  String anchorStateRoot = "0x171204401b433ea8af8751bcefba85201313b82ab43a79281a22a9a214ca8a33";

  // block skipped, recovery after 7 slots
  //  String anchorStateRoot = "0xed289ea2e127d26e57f20799512c7cf6ad1672c9dc93c394fe589352a53a1213";

  //  String anchorStateRoot = "0xd431b7131b6ef1e74b09825d3d26c4007eaf5b9a39dfcb82a0a4963d79721857";
  //  String anchorStateRoot = "0x5f39cf40f9f35640d0119093ef711e3e66594b31b3ff7f011676a4209783bb5b";
  //  String anchorStateRoot = "0xc99f15b57eea955b09524f42c58d2fd89063d0477de7c559462be39d92883df7";
  String anchorStateRoot = "0x8e032084e38fb529199e1cd6874b61ffbe0cf65686b626405254a508ac20aa0f";

  BeaconState anchorState;
  SignedBeaconBlock anchorBlock;

  static final int numberOfBlockToReplay = 8000;
  Stream<SignedBeaconBlock> blockStream;

  VoteTracker voteTracker;

  @BeforeEach
  public void setup() throws IOException {
    BLSConstants.disableBLSVerification();

    this.spec =
        TestSpecFactory.createMainnetElectra(
            builder ->
                builder
                    .committeeWeightEstimationAdjustmentFactor(
                        COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR)
                    .confirmationByzantineThreshold(CONFIRMATION_BYZANTINE_THRESHOLD)
                    .confirmationSlashingThreshold(CONFIRMATION_SLASHING_THRESHOLD));

    String fullStateUrl = jsonApiEndpoint + statePath + anchorStateRoot;
    System.out.println("Loading state from: " + fullStateUrl);
    Bytes stateBytes = BeaconCache.getCachedContent(fullStateUrl);
    anchorState = spec.deserializeBeaconState(stateBytes);
    System.out.println("State loaded as slot: " + anchorState.getSlot());

    System.out.println("Loading anchorBlock...");
    int anchorSlot = anchorState.getSlot().intValue();
    anchorBlock = loadBlock(anchorSlot).orElseThrow();
    System.out.println("Anchor block loaded: " + anchorBlock.getRoot());

    setupWithSpec();

    voteTracker = new VoteTracker(spec, storageSystem.recentChainData());

    blockStream =
        IntStream.range(1, numberOfBlockToReplay)
            .mapToObj(off -> loadBlock(anchorSlot + off))
            .flatMap(b -> b.stream());
  }

  private Optional<SignedBeaconBlock> loadBlock(int slot) {
    Bytes ssz = BeaconCache.getCachedContent(jsonApiEndpoint + blockPath + slot);
    if (ssz.size() < 512) { // not found error
      return Optional.empty();
    }
    SignedBeaconBlock block =
        spec.atSlot(UInt64.valueOf(slot))
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .sszDeserialize(ssz);

    return Optional.ofNullable(block);
  }

  private void setupWithSpec() {
    // Setting up spec and all dependants
    this.dataStructureUtil = new DataStructureUtil(spec);
    try {
      this.storageSystem =
          FileBackedStorageSystemBuilder.create()
              //        InMemoryStorageSystemBuilder.create()
              .storageMode(StateStorageMode.PRUNE)
              .specProvider(spec)
              .dataDir(Files.createTempDirectory(getClass().getSimpleName()))
              //            .numberOfValidators(VALIDATOR_COUNT)
              .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    //    this.genesis = chainBuilder.generateGenesis(UInt64.ZERO, false);
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

    SignedBlockAndState anchorBlockAndState = new SignedBlockAndState(anchorBlock, anchorState);
    AnchorPoint anchorPoint = AnchorPoint.fromInitialBlockAndState(spec, anchorBlockAndState);
    UInt64 anchorTime = spec.computeTimeAtSlot(anchorState, anchorState.getSlot());
    recentChainData.initializeFromAnchorPoint(anchorPoint, anchorTime);
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

  @Test
  void attCommitteesStats() {
    UInt64 anchorSlot = anchorState.getSlot();
    SpecVersion specVersion = spec.atSlot(anchorState.getSlot());

    Map<UInt64, List<Attestation>> slotAttestations = new HashMap<>();
    blockStream.forEach(
        block -> {
          for (Attestation attestation :
              block.getBeaconBlock().orElseThrow().getBody().getAttestations()) {
            List<Attestation> attestations =
                slotAttestations.computeIfAbsent(
                    attestation.getData().getSlot(), k -> new ArrayList<>());
            attestations.add(attestation);
          }
        });

    for (int i = 0; i < 32; i++) {
      UInt64 slot = anchorSlot.plus(i);
      Int2IntMap committeesSize =
          specVersion.beaconStateAccessors().getBeaconCommitteesSize(anchorState, slot);
      int sum = committeesSize.values().intParallelStream().sum();
      System.out.println(slot + ": " + sum + " ---> " + committeesSize);
    }
  }

  @Test
  void replay() {
    Iterator<SignedBeaconBlock> blockIterator = blockStream.iterator();
    int lastSlot = anchorBlock.getSlot().intValue();

    while (blockIterator.hasNext()) {
      SignedBeaconBlock block = blockIterator.next();

      for (int slot = lastSlot + 1; slot < block.getSlot().intValue(); slot++) {
        // empty slots
        forkChoice.processHead(UInt64.valueOf(slot));
      }
      lastSlot = block.getSlot().intValue();

      trackVotes(block.getBeaconBlock().orElseThrow());

      storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
      final SafeFuture<BlockImportResult> result =
          forkChoice.onBlock(block, Optional.empty(), blockBroadcastValidator, executionLayer);
      assertBlockImportedSuccessfully(result, false);

      forkChoice.processHead(block.getSlot());
    }
  }

  @Test
  void printTrueRandom() {
    Random random = new Random();
    Stream.generate(() -> random.nextInt(2_000_000))
        .limit(778)
        .sorted()
        .forEach(i -> System.out.println(i));
  }

  @Test
  void replayWithXatuAttestations() throws Exception {
    XatuConnector xatuConnector = XatuConnector.createDefault();
    xatuConnector.connect();

    Iterator<SignedBeaconBlock> blockIterator = blockStream.iterator();
    int lastSlot = anchorBlock.getSlot().intValue();
    BlockingQueue<XatuConnector.SlotAttestations> slotAttestationsQueue =
        xatuConnector.streamAttestationsReceivedDuringNextSlots(UInt64.valueOf(lastSlot));

    while (blockIterator.hasNext()) {
      SignedBeaconBlock block = blockIterator.next();

      for (int slot = lastSlot + 1; slot <= block.getSlot().intValue(); slot++) {
        // process slot start
        XatuConnector.SlotAttestations attestations =
            slotAttestationsQueue.poll(1, TimeUnit.MINUTES);
        if (attestations == null) {
          System.err.println("No attestations are retrieved, trying with a longer timeout");
          attestations = slotAttestationsQueue.poll(1, TimeUnit.HOURS);

          if (attestations == null) {
            throw new RuntimeException("No attestations were retrieved");
          }
        }

        UInt64 attestationsReceiveSlot = UInt64.valueOf(slot - 1);
        if (!attestationsReceiveSlot.equals(attestations.slot())) {
          throw new RuntimeException(attestationsReceiveSlot + " != " + attestations.slot());
        }
        trackVotes(attestations.attestations(), attestationsReceiveSlot);
        attestations
            .attestations()
            .forEach(
                attestation -> {
                  forkChoice.onAttestation(ValidatableAttestation.from(spec, attestation));
                });
        forkChoice.onTick(
            storageSystem.recentChainData().computeTimeAtSlot(UInt64.valueOf(slot)).times(1000),
            Optional.empty());
        storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(UInt64.valueOf(slot));
        forkChoice.processHead(UInt64.valueOf(slot));
      }

      // process block
      trackVotes(block.getBeaconBlock().orElseThrow());

      final SafeFuture<BlockImportResult> result =
          forkChoice.onBlock(block, Optional.empty(), blockBroadcastValidator, executionLayer);
      assertBlockImportedSuccessfully(result, false);

      //      forkChoice.processHead(block.getSlot());

      lastSlot = block.getSlot().intValue();
    }
  }

  void trackVotes(BeaconBlock block) {
    String votesInBlockString;
    int newVotesInBlock = 0;
    try {
      Set<VoteTracker.EpochVoter> newVotes =
          voteTracker.updateVotes(block.getBeaconBlock().orElseThrow());
      newVotesInBlock = newVotes.size();
      votesInBlockString = "" + newVotesInBlock;
    } catch (Exception e) {
      votesInBlockString = e.toString();
    }
    System.err.println(
        "Importing block: "
            + block.getSlot()
            + ", "
            + block.getRoot()
            + ", votes in block: "
            + newVotesInBlock);
    printVotesStat(block.getSlot());
  }

  void trackVotes(List<Attestation> attestations, UInt64 receivedSlot) {
    String votesInBlockString;
    int newVotesInSlot = 0;
    try {
      Set<VoteTracker.EpochVoter> newVotes = voteTracker.updateVotes(attestations);
      newVotesInSlot = newVotes.size();
      votesInBlockString = "" + newVotesInSlot;
    } catch (Exception e) {
      votesInBlockString = e.toString();
    }
    System.err.println("New votes from prev slot: " + receivedSlot + ", " + newVotesInSlot);
    printVotesStat(receivedSlot.increment());
  }

  void printVotesStat(UInt64 fromSlot) {
    for (int i = 1; i < 4; i++) {
      UInt64 slot = fromSlot.minus(i);
      Optional<SignedBeaconBlock> slotBlock =
          storageSystem.combinedChainDataClient().getBlockAtSlotExact(slot).join();
      System.err.println(
          "\tSlot/block votes/slot votes:\t"
              + slot
              + "\t"
              + slotBlock.map(b -> voteTracker.getVoteCountForBlock(b.getRoot())).orElse(0)
              + "\t"
              + voteTracker.getVoteCountInSlot(slot));
    }
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
