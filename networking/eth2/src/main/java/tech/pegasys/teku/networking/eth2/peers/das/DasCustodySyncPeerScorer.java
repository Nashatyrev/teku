package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;

import java.util.HashMap;
import java.util.Map;

/**
 * Scores peers with respect to missing data for the DAS custody which equally scores
 * requests for different epochs
 */
public class DasCustodySyncPeerScorer extends AbstractDasPeerScorer {

  public DasCustodySyncPeerScorer(Spec spec) {
    super(spec);
  }

  @Override
  protected int score(NodeId nodeId, int extraSubnetCount) {
    return epochEntries.values().stream().mapToInt(entry -> entry.score(nodeId, extraSubnetCount)).sum();
  }
}
