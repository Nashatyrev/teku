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

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@Disabled("For manual running only")
class ConfirmationRuleReplay {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
      mock(AvailabilityChecker.class);

  private StorageSystem storageSystem;
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
  List<SignedBeaconBlock> blocks = new ArrayList<>();

  static final int numberOfBlockToLoad = 1000;

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

    int startSlot = anchorState.getSlot().intValue();
    System.out.println(
        "Loading " + numberOfBlockToLoad + " blocks starting from " + startSlot + " ...");
    for (int i = 0; i < numberOfBlockToLoad; i++) {
      int slot = startSlot + i;
      System.out.print("Loading block " + slot + " ...");
      Optional<SignedBeaconBlock> block = loadBlock(slot);
      if (block.isPresent()) {
        System.out.println(block.get().getRoot());
        blocks.add(block.get());
      } else {
        System.out.println("<skipped>");
      }
    }

    setupWithSpec();

    voteTracker = new VoteTracker(spec, storageSystem.recentChainData());
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
    this.storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .specProvider(spec)
            //            .numberOfValidators(VALIDATOR_COUNT)
            .build();
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

    SignedBlockAndState anchorBlockAndState =
        new SignedBlockAndState(blocks.getFirst(), anchorState);
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
    for (SignedBeaconBlock block : blocks) {
      for (Attestation attestation :
          block.getBeaconBlock().orElseThrow().getBody().getAttestations()) {
        List<Attestation> attestations =
            slotAttestations.computeIfAbsent(
                attestation.getData().getSlot(), k -> new ArrayList<>());
        attestations.add(attestation);
      }
    }

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
    for (SignedBeaconBlock block : blocks) {
      if (block == blocks.getFirst()) {
        continue;
      }

      trackVotes(block.getBeaconBlock().orElseThrow());

      storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
      final SafeFuture<BlockImportResult> result =
          forkChoice.onBlock(block, Optional.empty(), blockBroadcastValidator, executionLayer);
      assertBlockImportedSuccessfully(result, false);
      forkChoice.processHead();
    }
  }

  void trackVotes(BeaconBlock block) {
    int newVotesInBlock = voteTracker.updateVotes(block.getBeaconBlock().orElseThrow());
    System.err.println(
        "Importing block: "
            + block.getSlot()
            + ", "
            + block.getRoot()
            + ", votes in block: "
            + newVotesInBlock);
    for (int i = 1; i < 4; i++) {
      UInt64 slot = block.getSlot().minus(i);
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

  private static class BeaconCache {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String cachePath = "./work.dir/http.cache";

    public static Bytes getCachedContent(String url) {
      try {
        Path file = getCachedFile(url, Path.of(cachePath));
        byte[] bytes = Files.readAllBytes(file);
        return Bytes.wrap(bytes);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Downloads a URL once and caches it in the given directory. Subsequent calls return the same
     * cached file.
     *
     * @param url Full HTTP/HTTPS URL of the resource (e.g. SSZ block)
     * @param cacheDir Local directory for cached files
     * @return Path to the cached file
     */
    public static Path getCachedFile(String url, Path cacheDir) throws Exception {
      Files.createDirectories(cacheDir);

      // Create a deterministic filename from SHA-256 of the URL
      String hash =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes()));
      Path target = cacheDir.resolve(hash);

      if (Files.notExists(target)) {
        HttpRequest req =
            HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/octet-stream") // or application/json
                .build();
        byte[] body = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
        Files.write(target, body);
      }
      return target;
    }
  }
}
