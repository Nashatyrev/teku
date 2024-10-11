package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Collection;
import java.util.Collections;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DasPeerCustodyCountSupplier;

public interface CustodyCalculator {

  static CustodyCalculator create(Spec spec, DasPeerCustodyCountSupplier peerCustodyCountSupplier) {
    return (nodeId, slot) -> {
      int custodySubnetCount = peerCustodyCountSupplier.getCustodyCountForPeer(nodeId);
      UInt64 epoch = spec.computeEpochAtSlot(slot);
      return spec.atEpoch(epoch)
          .miscHelpers()
          .toVersionEip7594()
          .map(
              miscHelpersEip7594 ->
                  miscHelpersEip7594.computeCustodyColumnIndexes(nodeId, custodySubnetCount))
          .orElse(Collections.emptyList());
    };
  }

  Collection<UInt64> computeCustodyColumnIndexes(UInt256 nodeId, UInt64 slot);
}
