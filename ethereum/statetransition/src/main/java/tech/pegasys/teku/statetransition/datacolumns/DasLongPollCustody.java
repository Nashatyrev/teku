package tech.pegasys.teku.statetransition.datacolumns;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public class DasLongPollCustody implements UpdatableDataColumnSidecarCustody {

  private final UpdatableDataColumnSidecarCustody delegate;
  private final AsyncRunner asyncRunner;
  private final Duration longPollRequestTimeout;

  @VisibleForTesting final PendingRequests pendingRequests = new PendingRequests();

  public DasLongPollCustody(
      UpdatableDataColumnSidecarCustody delegate,
      AsyncRunner asyncRunner,
      Duration longPollRequestTimeout) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.longPollRequestTimeout = longPollRequestTimeout;
  }

  @Override
  public void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar);
    final List<SafeFuture<DataColumnSidecar>> pendingRequests =
        this.pendingRequests.remove(DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
    for (SafeFuture<DataColumnSidecar> pendingRequest : pendingRequests) {
      pendingRequest.complete(dataColumnSidecar);
    }
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecar(columnId)
        .thenCompose(
            existingColumn -> {
              if (existingColumn.isPresent()) {
                return SafeFuture.completedFuture(existingColumn);
              } else {
                SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
                addPendingRequest(columnId, promise);
                return promise
                    .orTimeout(asyncRunner, longPollRequestTimeout)
                    .thenApply(Optional::of)
                    .exceptionally(
                        err -> {
                          if (ExceptionUtil.hasCause(err, TimeoutException.class)) {
                            return Optional.empty();
                          } else {
                            throw new CompletionException(err);
                          }
                        });
              }
            });
  }

  @Override
  public SafeFuture<List<ColumnSlotAndIdentifier>> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  private void addPendingRequest(
      final DataColumnIdentifier columnId, final SafeFuture<DataColumnSidecar> promise) {
    pendingRequests.add(columnId, promise);
  }

  @VisibleForTesting
  static class PendingRequests {
    final Map<DataColumnIdentifier, List<SafeFuture<DataColumnSidecar>>> requests = new HashMap<>();

    synchronized void add(
        final DataColumnIdentifier columnId, final SafeFuture<DataColumnSidecar> promise) {
      clearCancelledPendingRequests();
      requests.computeIfAbsent(columnId, __ -> new ArrayList<>()).add(promise);
    }

    synchronized List<SafeFuture<DataColumnSidecar>> remove(final DataColumnIdentifier columnId) {
      List<SafeFuture<DataColumnSidecar>> ret = requests.remove(columnId);
      return ret == null ? Collections.emptyList() : ret;
    }

    private void clearCancelledPendingRequests() {
      requests.values().forEach(promises -> promises.removeIf(CompletableFuture::isDone));
      requests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
  }
}
