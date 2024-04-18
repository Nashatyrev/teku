package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CustodySync {

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;
  private final int maxParallelColumnRequests = 1024;
  private final int minParallelColumnRequests = 512;

  record PendingRequest(
      ColumnSlotAndIdentifier columnId,
      SafeFuture<DataColumnSidecar> columnPromise
  ){}

  private Map<ColumnSlotAndIdentifier, PendingRequest> pendingRequests = new HashMap<>();

  public CustodySync(DataColumnSidecarCustody custody, DataColumnSidecarRetriever retriever) {
    this.custody = custody;
    this.retriever = retriever;
  }

  private synchronized void onRequestComplete(PendingRequest request) {
    DataColumnSidecar result = request.columnPromise.join();
    custody.onNewValidatedDataColumnSidecar(result);
    pendingRequests.remove(request.columnId.identifier());
    fillUpIfNeeded();
  }

  // TODO need to trigger periodically when initial sync is done

  private void fillUpIfNeeded() {
    if (pendingRequests.size() <= minParallelColumnRequests) {
      fillUp();
    }
  }

  private synchronized void fillUp() {
    int newRequestCount = maxParallelColumnRequests - pendingRequests.size();
    Set<ColumnSlotAndIdentifier> missingColumnsToRequest = custody.streamMissingColumns()
        .filter(c -> !pendingRequests.containsKey(c.identifier()))
        .limit(newRequestCount)
        .collect(Collectors.toSet());

    // cancel those which are not missing anymore for whatever reason
    Iterator<Map.Entry<ColumnSlotAndIdentifier, PendingRequest>> it = pendingRequests.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<ColumnSlotAndIdentifier, PendingRequest> pendingEntry = it.next();
      if (!missingColumnsToRequest.contains(pendingEntry.getKey())) {
        pendingEntry.getValue().columnPromise().cancel(true);
        it.remove();
      }
    }

    for (ColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
      SafeFuture<DataColumnSidecar> promise = retriever.retrieve(missingColumn);
      PendingRequest request = new PendingRequest(missingColumn, promise);
      pendingRequests.put(missingColumn, request);
      promise.thenAccept(__ -> onRequestComplete(request));
    }
  }

  public void start() {
    fillUp();
  }

  public synchronized void stop() {
    for (PendingRequest request : pendingRequests.values()) {
      request.columnPromise.cancel(true);
    }
    pendingRequests.clear();
  }
}
