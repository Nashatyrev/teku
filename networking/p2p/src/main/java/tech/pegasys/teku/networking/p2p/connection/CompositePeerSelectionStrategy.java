package tech.pegasys.teku.networking.p2p.connection;

import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class CompositePeerSelectionStrategy implements PeerSelectionStrategy {

  private final List<PeerSelectionStrategy> children;
  private final AtomicLong disconnectSelector = new AtomicLong();

  public CompositePeerSelectionStrategy(List<PeerSelectionStrategy> children) {
    this.children = children;
  }

  @Override
  public List<PeerAddress> selectPeersToConnect(
      P2PNetwork<?> network,
      PeerPools peerPools,
      Supplier<? extends Collection<DiscoveryPeer>> candidates) {
    return children.stream()
        .flatMap(
            childStrategy -> childStrategy.selectPeersToConnect(network, peerPools, candidates).stream())
        .toList();
  }

  @Override
  public List<Peer> selectPeersToDisconnect(P2PNetwork<?> network, PeerPools peerPools) {
    return getNextChildForDisconnect().selectPeersToDisconnect(network, peerPools);
  }

  private PeerSelectionStrategy getNextChildForDisconnect() {
    return children.get((int) (disconnectSelector.getAndIncrement() % children.size()));
  }
}
