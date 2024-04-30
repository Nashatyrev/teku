package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.DasPeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;

/**
 * Scores peers with respect to DAS sampling process
 * The sampling requirements could be:
 * - (not that urgent) historical sync: when blocks far from the head need to be imported and sampled
 * - (highly urgent) filling recent gaps: when recent block(s) were not sampled
 * - (urgent) awaiting sampling for the current slot
 * - (slightly urgent) planning for the future slot(s)
 */
public class DasSamplingPeerScorer extends AbstractDasPeerScorer {

  public DasSamplingPeerScorer(Spec spec) {
    super(spec);
  }

  @Override
  protected int score(NodeId nodeId, int extraSubnetCount) {
    // TODO
    throw new RuntimeException("Not implemented yet");
  }
}
