package tech.pegasys.teku.networking.eth2.peers.das;

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public interface DasCustodyPeers {

  List<NodeId> getCustodyPeersFor(UInt64 epoch, int dataColumnSubnetIndex);

  static DasCustodyPeers collectExistingNodes(P2PNetwork<?> network) {
    // TODO
    throw new UnsupportedOperationException("TODO");
  }
}
