package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface CustodyFunction {

  static CustodyFunction create(Spec spec, UInt256 nodeId, int totalCustodySubnetCount) {
    return slot -> new HashSet<>(
        spec.atEpoch(spec.computeEpochAtSlot(slot))
            .miscHelpers()
            .toVersionEip7594()
            .map(
                miscHelpers ->
                    miscHelpers.computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount))
            .orElse(Collections.emptyList()));
  }

  Set<UInt64> getCustodyColumns(UInt64 slot);
}
