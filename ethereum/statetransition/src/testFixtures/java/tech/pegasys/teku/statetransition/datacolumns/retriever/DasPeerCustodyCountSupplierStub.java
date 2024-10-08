package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;

import java.util.HashMap;
import java.util.Map;

public class DasPeerCustodyCountSupplierStub implements DasPeerCustodyCountSupplier {
  private final int defaultCount;
  private final Map<UInt256, Integer> customCounts = new HashMap<>();

  public DasPeerCustodyCountSupplierStub(int defaultCount) {
    this.defaultCount = defaultCount;
  }

  @Override
  public int getCustodyCountForPeer(UInt256 nodeId) {
    return customCounts.getOrDefault(nodeId, defaultCount);
  }

  public void addCustomCount(UInt256 nodeId, int count) {
    customCounts.put(nodeId, count);
  }
}
