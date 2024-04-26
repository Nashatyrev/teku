package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.DasPeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Scores peers with respect to DAS sampling process
 * The sampling requirements could be:
 * - (not that urgent) historical sync: when blocks far from the head need to be imported and sampled
 * - (highly urgent) filling recent gaps: when recent block(s) were not sampled
 * - (urgent) awaiting sampling for the current slot
 * - (slightly urgent) planning for the future slot(s)
 */
public class DasSamplingPeerScorer implements DasPeerScorer {

  private record Query(
      UInt64 epoch,
      int dataColumnSubnetIndex
  ){}

  @Override
  public void addSamplingQuery(UInt64 slot, int dataColumnSubnetIndex) {

  }

  @Override
  public void removeSamplingQuery(UInt64 slot, int dataColumnSubnetIndex) {

  }

  @Override
  public int scoreExistingPeer(NodeId peerId) {
    return 0;
  }

  @Override
  public int scoreCandidatePeer(DiscoveryPeer candidate) {
    return 0;
  }
}
