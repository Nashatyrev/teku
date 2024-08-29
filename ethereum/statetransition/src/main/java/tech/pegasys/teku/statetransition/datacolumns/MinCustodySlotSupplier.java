package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigEip7594;

public interface MinCustodySlotSupplier {

  static MinCustodySlotSupplier createFromSpec(Spec spec) {
    return currentSlot -> {
      UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
      int custodyPeriodEpochs =
          spec.getSpecConfig(currentEpoch)
              .toVersionEip7594()
              .map(NetworkingSpecConfigEip7594::getMinEpochsForDataColumnSidecarsRequests)
              .orElse(0);
      if (custodyPeriodEpochs == 0) {
        return currentSlot;
      } else {
        UInt64 minCustodyEpoch = currentEpoch.minusMinZero(custodyPeriodEpochs);
        return spec.computeStartSlotAtEpoch(minCustodyEpoch);
      }
    };
  }

  UInt64 getMinCustodySlot(UInt64 currentSlot);
}
