package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodySlotSupplier;

class PruningDasDb extends AbstractDelegatingDasDb {
  private final MinCustodySlotSupplier minCustodySlotSupplier;
  private volatile UInt64 maxSidecarSlot = UInt64.ZERO;

  public PruningDasDb(DataColumnSidecarDB delegate, MinCustodySlotSupplier minCustodySlotSupplier) {
    super(delegate);
    this.minCustodySlotSupplier = minCustodySlotSupplier;
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    super.addSidecar(sidecar);
    if (sidecar.getSlot().isGreaterThan(maxSidecarSlot)){
      maxSidecarSlot = sidecar.getSlot();
      UInt64 minCustodySlot = minCustodySlotSupplier.getMinCustodySlot(maxSidecarSlot);
      delegateDb.pruneAllSidecars(minCustodySlot);
    }
  }

  /**
   * @deprecated Pruning is performed automatically
   */
  @Override
  @Deprecated
  public void pruneAllSidecars(UInt64 tillSlot) {
    super.pruneAllSidecars(tillSlot);
  }
}
