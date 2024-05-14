/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class RequestSizeLimitingDasReqResp implements DataColumnReqResp {

  record RequestEntry(
      DataColumnIdentifier columnIdentifier, SafeFuture<DataColumnSidecar> promise) {}

  private final DataColumnReqResp delegate;
  private final int maxRequestSize;
  private final Map<UInt256, Queue<RequestEntry>> requestQueues = new HashMap<>();

  public RequestSizeLimitingDasReqResp(DataColumnReqResp delegate, int maxRequestSize) {
    this.delegate = delegate;
    this.maxRequestSize = maxRequestSize;
  }

  @Override
  public synchronized SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      UInt256 nodeId, DataColumnIdentifier columnIdentifier) {
    SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
    requestQueues
        .computeIfAbsent(nodeId, __ -> new ArrayDeque<>())
        .add(new RequestEntry(columnIdentifier, promise));
    return promise;
  }

  @Override
  public synchronized void flush() {}

  private synchronized void submitNextBatch(UInt256 nodeId) {
    Queue<RequestEntry> requestQueue = requestQueues.get(nodeId);
    if (requestQueue == null) {
      return;
    }
    Stream<SafeFuture<?>> batchFutures =
        Stream.generate(requestQueue::poll)
            .limit(maxRequestSize)
            .filter(Objects::nonNull)
            .map(
                request -> {
                  SafeFuture<DataColumnSidecar> promise =
                      delegate.requestDataColumnSidecar(nodeId, request.columnIdentifier());
                  promise.propagateTo(request.promise);
                  return promise;
                });
    if (requestQueue.isEmpty()) {
      requestQueues.remove(nodeId);
    }
    SafeFuture.allOf(batchFutures)
        .finish((__) -> nodeBatchCompleted(nodeId), (__) -> nodeBatchCompleted(nodeId));
  }

  private void nodeBatchCompleted(UInt256 nodeId) {
    submitNextBatch(nodeId);
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    return delegate.getCurrentRequestLimit(nodeId);
  }
}
