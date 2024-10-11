package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Collection;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DasPeerCustodyCountSupplier;

public interface NodeCustodyCalculator {

  static NodeCustodyCalculator create(Spec spec, UInt256 nodeId, int custodySubnetCount) {
    CustodyCalculator custodyCalculator =
        CustodyCalculator.create(spec, DasPeerCustodyCountSupplier.createStub(custodySubnetCount));
    return slot -> custodyCalculator.computeCustodyColumnIndexes(nodeId, slot);
  }

  Collection<UInt64> computeNodeCustodyColumnIndexes(UInt64 slot);
}
