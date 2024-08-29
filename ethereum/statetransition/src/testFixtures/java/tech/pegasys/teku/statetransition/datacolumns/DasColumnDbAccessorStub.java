package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.ColumnIdCachingDasDb;
import tech.pegasys.teku.statetransition.datacolumns.db.DasColumnDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.MissingDasColumnsDb;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class DasColumnDbAccessorStub implements DasColumnDbAccessor {

  public static Builder builder(DataColumnSidecarDB db) {
    return new Builder(db);
  }

  private final DasColumnDbAccessor db;
  private final FinalizedSlotSubscriberStub finalizedSlotSubscriberStub;

  public DasColumnDbAccessorStub(DasColumnDbAccessor db, FinalizedSlotSubscriberStub finalizedSlotSubscriberStub) {
    this.db = db;
    this.finalizedSlotSubscriberStub = finalizedSlotSubscriberStub;
  }

  public void setFinalizedSlot(UInt64 finalizedSlot) {
    finalizedSlotSubscriberStub.setFinalizedSlot(finalizedSlot);
  }

  @Override
  public SafeFuture<UInt64> getOrCalculateFirstCustodyIncompleteSlot() {
    return db.getOrCalculateFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return db.getSidecar(identifier);
  }

  @Override
  public SafeFuture<List<DataColumnIdentifier>> getColumnIdentifiers(UInt64 slot) {
    return db.getColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Set<UInt64>> getMissingColumnIndexes(SlotAndBlockRoot blockId) {
    return db.getMissingColumnIndexes(blockId);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return db.getFirstSamplerIncompleteSlot();
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    db.addSidecar(sidecar);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return db.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Set<ColumnSlotAndIdentifier>> getMissingColumnIds(SlotAndBlockRoot blockId) {
    return db.getMissingColumnIds(blockId);
  }

  public static class Builder {
    private final DataColumnSidecarDB db;
    private Spec spec;
    private MinCustodySlotSupplier minCustodySlotSupplier;
    private CanonicalBlockResolver canonicalBlockResolver;
    private CustodyFunction custodyFunction;
    private UInt256 nodeId = UInt256.ZERO;
    private Integer totalMyCustodySubnets;
    private Integer numberOfColumns;
    private FinalizedSlotSubscriberStub finalizedSlotSubscriberStub;
    private UInt64 currentSlot = UInt64.ZERO;
    private UInt64 currentFinalizedSlot = UInt64.ZERO;

    Builder(DataColumnSidecarDB db) {
      this.db = db;
    }

    public Builder spec(Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder minCustodySlotSupplier(MinCustodySlotSupplier minCustodySlotSupplier) {
      this.minCustodySlotSupplier = minCustodySlotSupplier;
      return this;
    }

    public Builder canonicalBlockResolver(CanonicalBlockResolver canonicalBlockResolver) {
      this.canonicalBlockResolver = canonicalBlockResolver;
      return this;
    }

    public Builder custodyFunction(CustodyFunction custodyFunction) {
      this.custodyFunction = custodyFunction;
      return this;
    }

    public Builder totalMyCustodySubnets(int totalMyCustodySubnets) {
      this.totalMyCustodySubnets = totalMyCustodySubnets;
      return this;
    }

    public Builder nodeId(UInt256 nodeId) {
      this.nodeId = nodeId;
      return this;
    }

    public Builder numberOfColumns(Integer numberOfColumns) {
      this.numberOfColumns = numberOfColumns;
      return this;
    }

    public Builder currentSlot(UInt64 currentSlot) {
      this.currentSlot = currentSlot;
      return this;
    }

    public Builder currentFinalizedSlot(UInt64 currentFinalizedSlot) {
      this.currentFinalizedSlot = currentFinalizedSlot;
      return this;
    }

    public DasColumnDbAccessorStub build() {
      if (minCustodySlotSupplier == null) {
        checkNotNull(spec);
        minCustodySlotSupplier = MinCustodySlotSupplier.createFromSpec(spec);
      }

      DataColumnSidecarDB autoPruneDb = db.withAutoPruning(minCustodySlotSupplier);

      if (numberOfColumns == null) {
        checkNotNull(spec);
        SpecConfigEip7594 configEip7594 =
            SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
        numberOfColumns = configEip7594.getNumberOfColumns();
      }

      ColumnIdCachingDasDb cachingDasDb = autoPruneDb.withColumnIdCaching(numberOfColumns);

      if (custodyFunction == null) {
        if (totalMyCustodySubnets == null) {
          checkNotNull(spec);
          SpecConfigEip7594 configEip7594 =
              SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
          totalMyCustodySubnets = configEip7594.getCustodyRequirement();
        }
        custodyFunction = CustodyFunction.create(spec, nodeId, totalMyCustodySubnets);
      }

      checkNotNull(canonicalBlockResolver);
      MissingDasColumnsDb missingDasColumnsDb =
          cachingDasDb.withMissingColumnsTracking(
              BlockBlobChecker.createFromCanonicalBlockResolver(canonicalBlockResolver),
              custodyFunction);

      finalizedSlotSubscriberStub = new FinalizedSlotSubscriberStub(currentFinalizedSlot);
      DasColumnDbAccessor dasColumnDbAccessor =
          missingDasColumnsDb.toDasColumnDbAccessor(
              canonicalBlockResolver,
              finalizedSlotSubscriberStub,
              minCustodySlotSupplier.getMinCustodySlot(currentSlot));

      return new DasColumnDbAccessorStub(dasColumnDbAccessor, finalizedSlotSubscriberStub);
    }
  }
}
