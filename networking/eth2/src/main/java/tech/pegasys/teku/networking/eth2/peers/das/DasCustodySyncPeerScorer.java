package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;

/**
 * Scores peers with respect to missing data for the DAS custody which equally scores requests for
 * different epochs
 */
public class DasCustodySyncPeerScorer extends AbstractDasPeerScorer {

  public DasCustodySyncPeerScorer(Spec spec) {
    super(spec);
  }

  @Override
  public DasScoreResult score(NodeId nodeId, int extraSubnetCount, DasCustodyPeers existingCustodies) {
    int score = epochEntries.values().stream()
        .mapToInt(entry -> entry.score(nodeId, extraSubnetCount))
        .sum();
    return new DasScoreResult(false, score);
  }
}
