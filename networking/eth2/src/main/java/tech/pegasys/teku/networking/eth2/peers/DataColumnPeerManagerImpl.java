package tech.pegasys.teku.networking.eth2.peers;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnPeerManager;

public class DataColumnPeerManagerImpl implements DataColumnPeerManager, PeerConnectedSubscriber<Eth2Peer> {

  private final Subscribers<PeerListener> listeners = Subscribers.create(true);

  @Override
  public void onConnected(Eth2Peer peer) {
    peerConnected(peer);
  }

  private void peerConnected(Eth2Peer peer) {
    listeners.forEach(l -> l.peerConnected(nodeIdToUInt(peer.getId())));
    peer.subscribeDisconnect((__1, __2) -> peerDisconnected(peer));
  }

  private void peerDisconnected(Eth2Peer peer) {
    listeners.forEach(l -> l.peerDisconnected(nodeIdToUInt(peer.getId())));
  }

  private UInt256 nodeIdToUInt(NodeId nodeId) {
    return UInt256.fromBytes(nodeId.toBytes());
  }

  @Override
  public void addPeerListener(PeerListener listener) {
    listeners.subscribe(listener);
  }

  @Override
  public void banNode(UInt256 node) {
    // TODO
  }
}
