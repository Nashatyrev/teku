package tech.pegasys.teku.das;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class DasTekuRunnerOtherNode {

  @Test
  void runBootNode() throws Exception {
    RunOtherNode.main();
    Thread.sleep(Duration.ofDays(1000));
  }
}
