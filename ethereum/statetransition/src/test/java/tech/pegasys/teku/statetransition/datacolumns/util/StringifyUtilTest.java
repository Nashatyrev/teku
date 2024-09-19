package tech.pegasys.teku.statetransition.datacolumns.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

public class StringifyUtilTest {

  record TestCase(IntStream indexes, String expectedString) {
    @Override
    public String toString() {
      return expectedString;
    }
  }

  static int maxIndexesLen = 128;

  static List<TestCase> testCases =
      List.of(
          new TestCase(IntStream.empty(), "[]"),
          new TestCase(range(0, 128), "[all]"),
          new TestCase(range(0, 128).skip(1), "[all except 0]"),
          new TestCase(range(0, 128).limit(127), "[all except 127]"),
          new TestCase(range(0, 128).filter(i -> !Set.of(14, 16, 18, 98).contains(i)), "[all except 14,16,18,98]"),
          new TestCase(IntStream.of(14, 16, 18, 98), "[14,16,18,98]"),
          new TestCase(IntStream.of(14, 15, 18, 98), "[14,15,18,98]"),
          new TestCase(IntStream.of(14, 15, 16, 98), "[14..16,98]"),
          new TestCase(range(0, 128).skip(64), "[64..127]"),
          new TestCase(range(0, 128).limit(64), "[0..63]"),
          new TestCase(range(0, 128).filter(i -> i % 3 != 0), "[1,2,4,5,...(77 more)..., 122,124,125,127], bitset: 0xb66ddbb66ddbb66ddbb66ddbb66ddbb6"),
          new TestCase(range(10, 100).filter(i -> !Set.of(14, 16, 18, 98).contains(i)), "[10..13,15,17,19..97,99]")
      );

  private static Stream<Arguments> provideTestCaseParameters() {
    return testCases.stream().map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("provideTestCaseParameters")
  void columnIndexesToString_test(TestCase testCase) {
    List<Integer> idxList = testCase.indexes.boxed().toList();
    String s =
        StringifyUtil.columnIndexesToString(idxList, maxIndexesLen);
    System.out.println(s);
    assertThat(s).isEqualTo("(len: " + idxList.size() + ") " + testCase.expectedString);
  }
}
