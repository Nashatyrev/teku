package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface DataColumnPeerManager {

  PeerRequest requestPeers(UInt64 slot, UInt64 columnIndex);

  void addPeerListener(PeerListener listener);

  void banNode(UInt256 node);


  interface PeerRequest {

    void dispose();
  }

  interface PeerListener {

    void peerConnected(UInt256 nodeId, int extraCustodySubnetCount);

    void peerDisconnected(UInt256 nodeId);
  }
}
