/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.features.Eip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.feature.eip7594.helpers.MiscHelpersEip7594;

// TODO improve thread-safety: external calls are better to do outside of the synchronize block to
// prevent potential dead locks
public class SimpleSidecarRetriever
    implements DataColumnSidecarRetriever, DataColumnPeerManager.PeerListener {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final Spec spec;
  private final DataColumnPeerSearcher peerSearcher;
  private final DasPeerCustodyCountSupplier custodyCountSupplier;
  private final DataColumnReqResp reqResp;
  private final AsyncRunner asyncRunner;
  private final Duration roundPeriod;
  private final int maxRequestCount;

  private final Map<DataColumnSlotAndIdentifier, RetrieveRequest> pendingRequests =
      new LinkedHashMap<>();
  private final Map<UInt256, ConnectedPeer> connectedPeers = new HashMap<>();
  private boolean started = false;
  private final AtomicLong retrieveCounter = new AtomicLong();
  private final AtomicLong errorCounter = new AtomicLong();

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DataColumnPeerSearcher peerSearcher,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final Duration roundPeriod) {
    this.spec = spec;
    this.peerSearcher = peerSearcher;
    this.custodyCountSupplier = custodyCountSupplier;
    this.asyncRunner = asyncRunner;
    this.roundPeriod = roundPeriod;
    this.reqResp = reqResp;
    peerManager.addPeerListener(this);
    this.maxRequestCount =
        Eip7594.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig())
            .getMaxRequestDataColumnSidecars();
  }

  private void startIfNecessary() {
    if (!started) {
      started = true;
      asyncRunner.runWithFixedDelay(
          this::nextRound, roundPeriod, err -> LOG.info("Unexpected error", err));
    }
  }

  @Override
  public synchronized SafeFuture<DataColumnSidecar> retrieve(
      final DataColumnSlotAndIdentifier columnId) {
    final DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest =
        peerSearcher.requestPeers(columnId.slot(), columnId.columnIndex());

    final RetrieveRequest existingRequest = pendingRequests.get(columnId);
    if (existingRequest == null) {
      final RetrieveRequest request = new RetrieveRequest(columnId, peerSearchRequest);
      pendingRequests.put(columnId, request);
      startIfNecessary();
      return request.result;
    } else {
      peerSearchRequest.dispose();
      return existingRequest.result;
    }
  }

  private synchronized List<RequestMatch> matchRequestsAndPeers() {
    disposeCompletedRequests();
    final RequestTracker ongoingRequestsTracker = createFromCurrentPendingRequests();
    return pendingRequests.entrySet().stream()
        .filter(entry -> entry.getValue().activeRpcRequest == null)
        .flatMap(
            entry -> {
              RetrieveRequest request = entry.getValue();
              return findBestMatchingPeer(request, ongoingRequestsTracker).stream()
                  .peek(peer -> ongoingRequestsTracker.decreaseAvailableRequests(peer.nodeId))
                  .map(peer -> new RequestMatch(peer, request));
            })
        .toList();
  }

  private Optional<ConnectedPeer> findBestMatchingPeer(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    final Collection<ConnectedPeer> matchingPeers =
        findMatchingPeers(request, ongoingRequestsTracker);

    // taking first the peers which were not requested yet, then peers which are less busy
    final Comparator<ConnectedPeer> comparator =
        Comparator.comparing((ConnectedPeer peer) -> request.getPeerRequestCount(peer.nodeId))
            .reversed()
            .thenComparing(
                (ConnectedPeer peer) ->
                    ongoingRequestsTracker.getAvailableRequestCount(peer.nodeId));
    return matchingPeers.stream().max(comparator);
  }

  private Collection<ConnectedPeer> findMatchingPeers(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    return connectedPeers.values().stream()
        .filter(peer -> peer.isCustodyFor(request.columnId))
        .filter(peer -> ongoingRequestsTracker.hasAvailableRequests(peer.nodeId))
        .toList();
  }

  private void disposeCompletedRequests() {
    final Iterator<Map.Entry<DataColumnSlotAndIdentifier, RetrieveRequest>> pendingIterator =
        pendingRequests.entrySet().iterator();
    while (pendingIterator.hasNext()) {
      final Map.Entry<DataColumnSlotAndIdentifier, RetrieveRequest> pendingEntry =
          pendingIterator.next();
      final RetrieveRequest pendingRequest = pendingEntry.getValue();
      if (pendingRequest.result.isDone()) {
        pendingIterator.remove();
        pendingRequest.peerSearchRequest.dispose();
        if (pendingRequest.activeRpcRequest != null) {
          pendingRequest.activeRpcRequest.promise().cancel(true);
        }
      }
    }
  }

  private synchronized void nextRound() {
    final List<RequestMatch> matches = matchRequestsAndPeers();
    for (final RequestMatch match : matches) {
      final SafeFuture<DataColumnSidecar> reqRespPromise =
          reqResp.requestDataColumnSidecar(
              match.peer.nodeId, match.request.columnId.toDataColumnIdentifier());
      match.request().onPeerRequest(match.peer().nodeId);
      match.request.activeRpcRequest =
          new ActiveRequest(
              reqRespPromise.whenComplete(
                  (sidecar, err) -> reqRespCompleted(match.request, sidecar, err)),
              match.peer);
    }

    final long activeRequestCount =
        pendingRequests.values().stream().filter(r -> r.activeRpcRequest != null).count();
    LOG.info(
        "[nyota] SimpleSidecarRetriever.nextRound: completed: {}, errored: {},  total pending: {}, active pending: {}, new active: {}, number of custody peers: {}",
        retrieveCounter,
        errorCounter,
        pendingRequests.size(),
        activeRequestCount,
        matches.size(),
        gatherAvailableCustodiesInfo());

    reqResp.flush();
  }

  @SuppressWarnings("unused")
  private synchronized void reqRespCompleted(
      final RetrieveRequest request,
      final DataColumnSidecar maybeResult,
      final Throwable maybeError) {
    if (maybeResult != null) {
      pendingRequests.remove(request.columnId);
      request.result.completeAsync(maybeResult, asyncRunner);
      request.peerSearchRequest.dispose();
      retrieveCounter.incrementAndGet();
    } else {
      request.activeRpcRequest = null;
      errorCounter.incrementAndGet();
    }
  }

  private String gatherAvailableCustodiesInfo() {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.ELECTRA);
    final Map<UInt64, Long> colIndexToCount =
        connectedPeers.values().stream()
            .flatMap(p -> p.getNodeCustodyIndexes(specVersion).stream())
            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
    final int numberOfColumns = Eip7594.required(specVersion.getConfig()).getNumberOfColumns();
    IntStream.range(0, numberOfColumns)
        .mapToObj(UInt64::valueOf)
        .forEach(idx -> colIndexToCount.putIfAbsent(idx, 0L));
    colIndexToCount.replaceAll((colIdx, count) -> Long.min(3, count));
    final Map<Long, Long> custodyCountToPeerCount =
        colIndexToCount.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));
    return new TreeMap<>(custodyCountToPeerCount)
        .entrySet().stream()
            .map(
                entry -> {
                  String peerCnt = entry.getKey() == 3 ? "3+" : "" + entry.getKey();
                  return entry.getValue() + " cols: " + peerCnt + " peers";
                })
            .collect(Collectors.joining(","));
  }

  @Override
  public synchronized void peerConnected(final UInt256 nodeId) {
    LOG.info(
        "[nyota] SimpleSidecarRetriever.peerConnected: {}",
        "0x..." + nodeId.toHexString().substring(58));
    connectedPeers.put(nodeId, new ConnectedPeer(nodeId));
  }

  @Override
  public synchronized void peerDisconnected(final UInt256 nodeId) {
    LOG.info(
        "[nyota] SimpleSidecarRetriever.peerDisconnected: {}",
        "0x..." + nodeId.toHexString().substring(58));
    connectedPeers.remove(nodeId);
  }

  private record ActiveRequest(SafeFuture<DataColumnSidecar> promise, ConnectedPeer peer) {}

  private static class RetrieveRequest {
    final DataColumnSlotAndIdentifier columnId;
    final DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest;
    final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
    final Map<UInt256, Integer> peerRequestCount = new HashMap<>();
    volatile ActiveRequest activeRpcRequest = null;

    private RetrieveRequest(
        final DataColumnSlotAndIdentifier columnId,
        final DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest) {
      this.columnId = columnId;
      this.peerSearchRequest = peerSearchRequest;
    }

    public void onPeerRequest(final UInt256 peerId) {
      peerRequestCount.compute(peerId, (__, curCount) -> curCount == null ? 1 : curCount + 1);
    }

    public int getPeerRequestCount(final UInt256 peerId) {
      return peerRequestCount.getOrDefault(peerId, 0);
    }
  }

  private class ConnectedPeer {
    final UInt256 nodeId;
    final Cache<CacheKey, Set<UInt64>> custodyIndexesCache = LRUCache.create(2);

    private record CacheKey(SpecVersion specVersion, int custodyCount) {}

    public ConnectedPeer(final UInt256 nodeId) {
      this.nodeId = nodeId;
    }

    private Set<UInt64> calcNodeCustodyIndexes(final CacheKey cacheKey) {
      return new HashSet<>(
          MiscHelpersEip7594.required(cacheKey.specVersion().miscHelpers())
              .computeCustodyColumnIndexes(nodeId, cacheKey.custodyCount()));
    }

    private Set<UInt64> getNodeCustodyIndexes(final SpecVersion specVersion) {
      return custodyIndexesCache.get(
          new CacheKey(specVersion, custodyCountSupplier.getCustodyCountForPeer(nodeId)),
          this::calcNodeCustodyIndexes);
    }

    public boolean isCustodyFor(final DataColumnSlotAndIdentifier columnId) {
      return getNodeCustodyIndexes(spec.atSlot(columnId.slot())).contains(columnId.columnIndex());
    }
  }

  private record RequestMatch(ConnectedPeer peer, RetrieveRequest request) {}

  private RequestTracker createFromCurrentPendingRequests() {
    final Map<UInt256, Integer> pendingRequestsCount =
        pendingRequests.values().stream()
            .map(r -> r.activeRpcRequest)
            .filter(Objects::nonNull)
            .map(r -> r.peer().nodeId)
            .collect(Collectors.groupingBy(r -> r, Collectors.reducing(0, e -> 1, Integer::sum)));
    return new RequestTracker(pendingRequestsCount);
  }

  private class RequestTracker {
    private final Map<UInt256, Integer> pendingRequestsCount;

    private RequestTracker(final Map<UInt256, Integer> pendingRequestsCount) {
      this.pendingRequestsCount = pendingRequestsCount;
    }

    int getAvailableRequestCount(final UInt256 nodeId) {
      return Integer.min(maxRequestCount, reqResp.getCurrentRequestLimit(nodeId))
          - pendingRequestsCount.getOrDefault(nodeId, 0);
    }

    boolean hasAvailableRequests(final UInt256 nodeId) {
      return getAvailableRequestCount(nodeId) > 0;
    }

    void decreaseAvailableRequests(final UInt256 nodeId) {
      pendingRequestsCount.compute(nodeId, (__, cnt) -> cnt == null ? 1 : cnt + 1);
    }
  }
}
