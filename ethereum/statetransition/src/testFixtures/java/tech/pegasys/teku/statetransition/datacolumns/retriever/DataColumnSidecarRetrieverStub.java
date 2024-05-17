package tech.pegasys.teku.statetransition.datacolumns.retriever;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;

import java.util.ArrayList;
import java.util.List;

public class DataColumnSidecarRetrieverStub implements DataColumnSidecarRetriever {

  public record RetrieveRequest(
      ColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> promise) {}

  public List<RetrieveRequest> requests = new ArrayList<>();

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(ColumnSlotAndIdentifier columnId) {
    RetrieveRequest request = new RetrieveRequest(columnId, new SafeFuture<>());
    requests.add(request);
    return request.promise;
  }
}
