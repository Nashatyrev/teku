package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.DasPeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Scores peers with respect to missing data for the DAS custody
 * which can fill its gaps in a more relaxed way
 */
public class DasCustodySyncPeerScorer implements DasPeerScorer {

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
