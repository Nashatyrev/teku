package tech.pegasys.teku.networking.eth2.peers.das;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public record DasScoreResult(boolean isMandatory, int score) implements Comparable<DasScoreResult> {
  static final DasScoreResult ZERO = new DasScoreResult(false, 0);
  static final Comparator<DasScoreResult> COMPARATOR =
      Comparator.comparing(DasScoreResult::isMandatory).thenComparing(DasScoreResult::score);

  public DasScoreResult add(DasScoreResult other) {
    return new DasScoreResult(this.isMandatory || other.isMandatory(), this.score + other.score());
  }

  @Override
  public int compareTo(@NotNull DasScoreResult o) {
    return COMPARATOR.compare(this, o);
  }
}
