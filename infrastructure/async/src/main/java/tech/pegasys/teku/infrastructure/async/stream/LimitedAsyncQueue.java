package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

class LimitedAsyncQueue<T> implements AsyncQueue<T> {

  private final int maxSize;

  private final Queue<T> items = new LinkedList<>();
  private final Queue<SafeFuture<T>> takers = new LinkedList<>();

  public LimitedAsyncQueue(int maxSize) {
    this.maxSize = maxSize;
  }

  // Adds an item to the queue
  public synchronized void put(T item) {
    if (!takers.isEmpty()) {
      // If there are pending takers, complete one with the item
      CompletableFuture<T> taker = takers.poll();
      taker.complete(item);
    } else {
      // Otherwise, add the item to the items queue
      if (items.size() >= maxSize) {
        throw new IllegalStateException("Buffer size overflow: " + maxSize);
      }
      items.offer(item);
    }
  }

  // Returns a CompletableFuture that will be completed when an item is available
  public synchronized SafeFuture<T> take() {
    if (!items.isEmpty()) {
      // If items are available, return a completed future
      T item = items.poll();
      return SafeFuture.completedFuture(item);
    } else {
      // If no items, create a new CompletableFuture and add it to takers
      SafeFuture<T> future = new SafeFuture<>();
      takers.offer(future);
      return future;
    }
  }
}
