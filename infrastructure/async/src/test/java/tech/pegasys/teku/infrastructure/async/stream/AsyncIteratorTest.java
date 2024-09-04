package tech.pegasys.teku.infrastructure.async.stream;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncIteratorTest {

  @Test
  void sanityTest() {
    List<SafeFuture<Integer>> futures =
        Stream.generate(() -> new SafeFuture<Integer>()).limit(5).toList();

    ArrayList<Integer> collector = new ArrayList<>();

    SafeFuture<List<Integer>> listPromise =
        AsyncStream.create(futures.iterator())
            .flatMap(AsyncStream::create)
            .flatMap(
                i -> AsyncStream.create(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .limit(10)
            .collect(collector);

    assertThat(collector).isEmpty();

    futures.get(1).complete(1);

    assertThat(collector).isEmpty();

    futures.get(0).complete(0);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140);
    assertThat(listPromise).isNotDone();

    futures.get(4).completeExceptionally(new RuntimeException("test"));

    assertThat(listPromise).isNotDone();

    futures.get(2).complete(2);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240);
    assertThat(listPromise).isNotDone();

    futures.get(3).complete(3);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);
    assertThat(listPromise)
        .isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300));
  }

  @Test
  void longStreamOfCompletedFuturesShouldNotCauseStackOverflow() {
    List<Integer> ints = AsyncStream.create(IntStream.range(0, 10000).boxed().iterator())
        .map(SafeFuture::completedFuture)
        .flatMap(AsyncStream::create)
        .toList()
        .join();

    assertThat(ints).hasSize(10000);
  }

  @Test
  void longStreamOfFlatMapShouldNotCauseStackOverflow() {
    List<Integer> ints = AsyncStream.create(IntStream.range(0, 10000).boxed().iterator())
        .flatMap(AsyncStream::of)
        .toList()
        .join();

    assertThat(ints).hasSize(10000);
  }
}
