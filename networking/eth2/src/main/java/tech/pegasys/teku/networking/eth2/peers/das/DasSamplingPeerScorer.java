package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;

import java.util.Collection;
import java.util.Optional;

/** Scores peers with respect to DAS sampling process */
public class DasSamplingPeerScorer extends AbstractDasPeerScorer {

  private final int minSamplingPeerCount;
  private final int targetSamplingPeerCount;

  public DasSamplingPeerScorer(Spec spec, int minSamplingPeerCount, int targetSamplingPeerCount) {
    super(spec);
    this.minSamplingPeerCount = minSamplingPeerCount;
    this.targetSamplingPeerCount = targetSamplingPeerCount;
  }

  @Override
  public DasScoreResult score(
      NodeId nodeId, int extraSubnetCount, DasCustodyPeers existingCustodies) {
    Collection<SubnetRequestPeerCount> missingPeerCountForRequests =
        collectRequestPeerCount(existingCustodies, nodeId);
    return missingPeerCountForRequests.stream()
        .map(
            requestEntry -> {
              Optional<SszBitvector> subnetsBitmap =
                  epochEntries
                      .get(requestEntry.request().epoch)
                      .subnetsCalculator
                      .calculateSubnets(nodeId, extraSubnetCount);
              boolean suitesRequest =
                  subnetsBitmap
                      .map(bitmap -> bitmap.getBit(requestEntry.request().subnetIndex()))
                      .orElse(false);
              return suitesRequest
                  ? calculateRequestScore(requestEntry.peerCount())
                  : DasScoreResult.ZERO;
            })
        .reduce(DasScoreResult.ZERO, DasScoreResult::add);
  }

  private DasScoreResult calculateRequestScore(int requestPeerCount) {
    if (requestPeerCount < minSamplingPeerCount) {
      return new DasScoreResult(true, squared(minSamplingPeerCount - requestPeerCount));
    } else if (requestPeerCount < targetSamplingPeerCount) {
      return new DasScoreResult(false, targetSamplingPeerCount - requestPeerCount);
    } else {
      return DasScoreResult.ZERO;
    }
  }

  private static int squared(int n) {
    return n * n;
  }

  record SubnetRequestPeerCount(SubnetRequest request, int peerCount) {}

  private Collection<SubnetRequestPeerCount> collectRequestPeerCount(
      DasCustodyPeers existingCustodies, NodeId excludingPeer) {
    return getAllRequests().stream()
        .map(
            request -> {
              long peerCount =
                  existingCustodies.getCustodyPeersFor(request.epoch, request.subnetIndex).stream()
                      .filter(peer -> !peer.equals(excludingPeer))
                      .count();
              return new SubnetRequestPeerCount(request, (int) peerCount);
            })
        .toList();
  }

  record SubnetRequest(UInt64 epoch, int subnetIndex, int requestCount) {}

  private Collection<SubnetRequest> getAllRequests() {
    return epochEntries.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().subnetToQueryCounter.entrySet().stream()
                    .map(
                        subnetToCount ->
                            new SubnetRequest(
                                entry.getKey(), subnetToCount.getKey(), subnetToCount.getValue())))
        .toList();
  }
}
