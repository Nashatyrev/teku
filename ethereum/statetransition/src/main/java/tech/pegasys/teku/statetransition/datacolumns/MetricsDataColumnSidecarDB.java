package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class MetricsDataColumnSidecarDB implements DataColumnSidecarDB {
  private final DataColumnSidecarDB delegate;
  private final Counter streamColumnIdsCounter;

  public MetricsDataColumnSidecarDB(DataColumnSidecarDB delegate, MetricsSystem metricsSystem) {
    this.delegate = delegate;
    streamColumnIdsCounter = metricsSystem.createCounter(
        TekuMetricCategory.STORAGE,
        "das_stream_column_identifiers",
        "Count of streamColumnIdentifiers() calls");

  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegate.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delegate.getFirstSamplerIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnIdentifier identifier) {
    return delegate.getSidecar(identifier);
  }

  @Override
  public SafeFuture<Stream<DataColumnIdentifier>> streamColumnIdentifiers(UInt64 slot) {
    streamColumnIdsCounter.inc();
    return delegate.streamColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(UInt64 slot) {
    return delegate.setFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(UInt64 slot) {
    return delegate.setFirstSamplerIncompleteSlot(slot);
  }

  @Override
  public void addSidecar(DataColumnSidecar sidecar) {
    delegate.addSidecar(sidecar);
  }

  @Override
  public void pruneAllSidecars(UInt64 tillSlot) {
    delegate.pruneAllSidecars(tillSlot);
  }
}
