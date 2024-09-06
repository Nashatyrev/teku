package tech.pegasys.teku.statetransition.datacolumns.db;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class DataColumnSidecarDbAccessorBuilder {

  private final DataColumnSidecarDB db;
  private Spec spec;
  private MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  private final AutoPruneDbBuilder autoPruneDbBuilder = new AutoPruneDbBuilder();


  DataColumnSidecarDbAccessorBuilder(DataColumnSidecarDB db) {
    this.db = db;
  }

  public DataColumnSidecarDbAccessorBuilder spec(Spec spec) {
    this.spec = spec;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder minCustodyPeriodSlotCalculator(MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator) {
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder withAutoPrune(Consumer<AutoPruneDbBuilder> builderConsumer) {
    builderConsumer.accept(this.autoPruneDbBuilder);
    return this;
  }

  public DataColumnSidecarDbAccessor build() {
    return autoPruneDbBuilder.build();
  }

  MinCustodyPeriodSlotCalculator getMinCustodyPeriodSlotCalculator() {
    if (minCustodyPeriodSlotCalculator == null) {
      checkNotNull(spec);
      minCustodyPeriodSlotCalculator = MinCustodyPeriodSlotCalculator.createFromSpec(spec);
    }
    return minCustodyPeriodSlotCalculator;
  }

  public class AutoPruneDbBuilder {
    private int pruneMarginSlots = 0;
    private int prunePeriodInSlots = 1;

    /**
     * Additional period in slots to retain data column sidecars in DB before pruning
     */
    public AutoPruneDbBuilder pruneMarginSlots(int pruneMarginSlots) {
      this.pruneMarginSlots = pruneMarginSlots;
      return this;
    }

    /**
     * Specifies how often (in slots) the db prune will be performed
     * 1 means that the prune is to be called every slot
     */
    public void prunePeriodSlots(int prunePeriodInSlots) {
      this.prunePeriodInSlots = prunePeriodInSlots;
    }

    AutoPruningDasDb build() {
      return new AutoPruningDasDb(db, getMinCustodyPeriodSlotCalculator(), pruneMarginSlots, prunePeriodInSlots);
    }
  }
}
