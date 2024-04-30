package tech.pegasys.teku.networking.eth2.peers.das;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.eth2.peers.DasPeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrUtil;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;

public abstract class AbstractDasPeerScorer implements DasPeerScorer {

  protected static class EpochEntry {
    private final NodeIdToDataColumnSidecarSubnetsCalculator subnetsCalculator;
    private final Map<Integer, Integer> subnetToQueryCounter = new HashMap<>();

    public EpochEntry(NodeIdToDataColumnSidecarSubnetsCalculator subnetsCalculator) {
      this.subnetsCalculator = subnetsCalculator;
    }

    public int score(NodeId nodeId, int extraSubnetCount) {
      return subnetsCalculator
          .calculateSubnets(nodeId, extraSubnetCount)
          .map(
              subnets ->
                  subnets
                      .streamAllSetBits()
                      .map(nodeSubnet -> subnetToQueryCounter.getOrDefault(nodeSubnet, 0))
                      .sum())
          .orElse(0);
    }

    public void addSubnet(int subnet) {
      subnetToQueryCounter.compute(subnet, (__, counter) -> counter == null ? 1 : counter + 1);
    }

    public boolean removeSubnet(int subnet) {
      subnetToQueryCounter.compute(
          subnet,
          (__, counter) -> {
            if (counter == null) {
              throw new IllegalStateException("Mismatching remove request");
            }
            int newCounter = counter - 1;
            return newCounter > 0 ? newCounter : null;
          });
      return subnetToQueryCounter.isEmpty();
    }
  }

  protected final Spec spec;
  protected final Map<UInt64, EpochEntry> epochEntries = new HashMap<>();
  // TODO
  protected final Function<NodeId, Integer> extraCustodySubnetSupplier = (__) -> 0;

  public AbstractDasPeerScorer(Spec spec) {
    this.spec = spec;
  }

  @Override
  public synchronized void addSamplingQuery(UInt64 slot, int dataColumnSubnetIndex) {
    epochEntries
        .computeIfAbsent(
            spec.computeEpochAtSlot(slot),
            __ ->
                new EpochEntry(NodeIdToDataColumnSidecarSubnetsCalculator.createAtSlot(spec, slot)))
        .addSubnet(dataColumnSubnetIndex);
  }

  @Override
  public synchronized void removeSamplingQuery(UInt64 slot, int dataColumnSubnetIndex) {
    epochEntries.compute(
        spec.computeEpochAtSlot(slot),
        (epoch, entry) -> {
          if (entry == null) {
            throw new IllegalStateException("Mismatching remove request");
          }
          return entry.removeSubnet(dataColumnSubnetIndex) ? null : entry;
        });
  }

  protected abstract int score(NodeId nodeId, int extraSubnetCount);

  @Override
  public synchronized int scoreExistingPeer(NodeId nodeId) {
    return score(nodeId, extraCustodySubnetSupplier.apply(nodeId));
  }

  @Override
  public synchronized int scoreCandidatePeer(DiscoveryPeer candidate) {
    return score(
        MultiaddrUtil.getNodeId(candidate), candidate.getDasExtraCustodySubnetCount());
  }
}
