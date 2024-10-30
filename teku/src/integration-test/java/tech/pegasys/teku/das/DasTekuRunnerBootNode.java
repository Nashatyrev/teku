package tech.pegasys.teku.das;

import org.junit.jupiter.api.Test;

import java.time.Duration;

public class DasTekuRunnerBootNode {

  @Test
  void runBootNode() throws Exception {
    RunBootNode.main();
    Thread.sleep(Duration.ofDays(1000));
  }
}
