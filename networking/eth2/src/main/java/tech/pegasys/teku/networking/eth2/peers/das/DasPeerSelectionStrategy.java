package tech.pegasys.teku.networking.eth2.peers.das;

import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class DasPeerSelectionStrategy implements PeerSelectionStrategy {

  private final NodeIdToDataColumnSidecarSubnetsCalculator subnetsCalculator;
  private final Function<NodeId, Integer> extraCustodySubnetSupplier;
  private final List<DasPeerScorer> scorers;
  private final int maxNonMandatoryPeers;

  public DasPeerSelectionStrategy(
      NodeIdToDataColumnSidecarSubnetsCalculator subnetsCalculator,
      Function<NodeId, Integer> extraCustodySubnetSupplier,
      List<DasPeerScorer> scorers,
      int maxNonMandatoryPeers) {
    this.subnetsCalculator = subnetsCalculator;
    this.extraCustodySubnetSupplier = extraCustodySubnetSupplier;
    this.scorers = scorers;
    this.maxNonMandatoryPeers = maxNonMandatoryPeers;
  }

  @SuppressWarnings("UnreachableCode")
  @Override
  public List<PeerAddress> selectPeersToConnect(
      P2PNetwork<?> network,
      PeerPools peerPools,
      Supplier<? extends Collection<DiscoveryPeer>> candidates) {
    DasCustodyPeers existingNodes = DasCustodyPeers.collectExistingNodes(network);
    candidates.get().stream()
        .map(
            candidate -> {
              DasScoreResult candidateScore =
                  scorers.stream()
                      .map(
                          scorer ->
                              scorer.score(
                                  MultiaddrUtil.getNodeId(candidate),
                                  candidate.getDasExtraCustodySubnetCount(),
                                  existingNodes))
                      .reduce(DasScoreResult.ZERO, DasScoreResult::add);
              return Pair.of(candidate, candidateScore);
            })
        .sorted(Comparator.comparing(Pair::getRight).reversed());
    return List.of();
  }

  @Override
  public List<Peer> selectPeersToDisconnect(P2PNetwork<?> network, PeerPools peerPools) {
    return List.of();
  }
}
