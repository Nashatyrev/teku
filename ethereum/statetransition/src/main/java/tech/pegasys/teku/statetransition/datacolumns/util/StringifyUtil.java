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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;

public class StringifyUtil {

  public static String columnIndexesToString(Collection<Integer> indexes, int maxColumns) {
    if (indexes.isEmpty()) {
      return "[]";
    } else if (indexes.size() == maxColumns) {
      return "[0.." + (maxColumns - 1) + "]";
    } else if (indexes.size() <= 16) {
      return "(len: " + indexes.size() + ") [" + sortAndJoin(indexes) + "]";
    } else if (maxColumns - indexes.size() <= 16) {
      Set<Integer> exceptIndexes =
          IntStream.range(0, maxColumns).boxed().collect(Collectors.toSet());
      exceptIndexes.removeAll(indexes);
      return "(len: "
          + indexes.size()
          + ") [0.."
          + (maxColumns - 1)
          + " except "
          + sortAndJoin(exceptIndexes)
          + "]";
    } else {
      List<IntRange> ranges = reduceToIntRanges(indexes);
      if (ranges.size() <= 16) {
        return "(len: "
            + indexes.size()
            + ") ["
            + ranges.stream().map(Objects::toString).collect(Collectors.joining(", "))
            + "]";
      } else {
        BitSet bitSet = new BitSet(maxColumns);
        indexes.forEach(bitSet::set);
        return "(len: " + indexes.size() + ") bitset: " + Bytes.of(bitSet.toByteArray());
      }
    }
  }

  private record IntRange(int first, int last) {

    static final IntRange EMPTY = new IntRange(0, -1);

    static IntRange of(int i) {
      return new IntRange(i, i);
    }

    static List<IntRange> union(List<IntRange> left, List<IntRange> right) {
      if (left.isEmpty()) {
        return right;
      } else if (right.isEmpty()) {
        return right;
      } else {
        return Stream.of(
                left.stream().limit(left.size() - 1),
                left.getLast().union(right.getFirst()).stream(),
                right.stream().skip(1))
            .flatMap(s -> s)
            .toList();
      }
    }

    boolean isEmpty() {
      return first > last;
    }

    boolean isSingle() {
      return first == last;
    }

    List<IntRange> union(IntRange other) {
      if (this.isEmpty()) {
        return List.of(other);
      } else if (other.isEmpty()) {
        return List.of(this);
      } else if (other.first() > this.last() + 1) {
        return List.of(this, other);
      } else if (this.first() > other.last() + 1) {
        return List.of(other, this);
      } else {
        return List.of(
            new IntRange(min(this.first(), other.first()), max(this.last(), other.last())));
      }
    }

    @Override
    public String toString() {
      if (isEmpty()) {
        return "<empty>";
      } else if (isSingle()) {
        return Integer.toString(first());
      } else {
        return first() + ".." + last();
      }
    }
  }

  private static List<IntRange> reduceToIntRanges(Collection<Integer> nums) {
    return nums.stream()
        .sorted()
        .map(i -> List.of(IntRange.of(i)))
        .reduce(IntRange::union)
        .orElse(Collections.emptyList());
  }

  private static String sortAndJoin(Collection<Integer> nums) {
    return nums.stream().sorted().map(Objects::toString).collect(Collectors.joining(","));
  }
}
