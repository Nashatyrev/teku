package tech.pegasys.teku.networking.eth2.peers.das;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public interface DasPeerScorer {

  DasScoreResult score(NodeId nodeId, int extraSubnetCount, DasCustodyPeers existingCustodies);

  void addSamplingQuery(UInt64 slot, int dataColumnSubnetIndex);

  void removeSamplingQuery(UInt64 slot, int dataColumnSubnetIndex);
}
