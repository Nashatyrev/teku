package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;

public interface DataColumnPeerManager extends DataColumnPeerSearcher {

  void addPeerListener(PeerListener listener);

  void banNode(UInt256 node);

  interface PeerListener {

    void peerConnected(UInt256 nodeId, int extraCustodySubnetCount);

    void peerDisconnected(UInt256 nodeId);
  }
}
