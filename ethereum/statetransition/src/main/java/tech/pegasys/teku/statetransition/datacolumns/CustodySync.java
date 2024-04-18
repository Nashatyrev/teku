package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CustodySync implements SlotEventsChannel {

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;
  private final int maxPendingColumnRequests = 1024;
  private final int minPendingColumnRequests = 512;

  private Map<ColumnSlotAndIdentifier, PendingRequest> pendingRequests = new HashMap<>();
  private boolean started = false;

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

  private void fillUpIfNeeded() {
    if (started && pendingRequests.size() <= minPendingColumnRequests) {
      fillUp();
    }
  }

  private synchronized void fillUp() {
    int newRequestCount = maxPendingColumnRequests - pendingRequests.size();
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
    started = true;
    fillUp();
  }

  public synchronized void stop() {
    started = false;
    for (PendingRequest request : pendingRequests.values()) {
      request.columnPromise.cancel(true);
    }
    pendingRequests.clear();
  }

  @Override
  public void onSlot(UInt64 slot) {
    fillUpIfNeeded();
  }

  private record PendingRequest(
      ColumnSlotAndIdentifier columnId,
      SafeFuture<DataColumnSidecar> columnPromise
  ){}
}
