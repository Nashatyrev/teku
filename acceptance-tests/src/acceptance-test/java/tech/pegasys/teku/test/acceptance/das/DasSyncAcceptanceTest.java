/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.test.acceptance.das;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DasSyncAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldSyncToNodeWithGreaterFinalizedEpoch() throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(createConfigBuilder().withRealNetwork().build());

    primaryNode.start();
    UInt64 genesisTime = primaryNode.getGenesisTime();
    final TekuBeaconNode lateJoiningNode =
        createLateJoiningNode(primaryNode, genesisTime.intValue());
    primaryNode.waitForEpochAtOrAbove(4);

    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
  }

  private TekuBeaconNode createLateJoiningNode(
      final TekuBeaconNode primaryNode, final int genesisTime) throws IOException {
    return createTekuBeaconNode(
        createConfigBuilder()
            .withGenesisTime(genesisTime)
            .withRealNetwork()
            .withPeers(primaryNode)
            .withInteropValidators(0, 0)
            .build());
  }

  private TekuNodeConfigBuilder createConfigBuilder() throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withAltairEpoch(UInt64.valueOf(0))
        .withBellatrixEpoch(UInt64.valueOf(0))
        .withCapellaEpoch(UInt64.valueOf(0))
        .withDenebEpoch(UInt64.valueOf(0))
        .withElectraEpoch(UInt64.valueOf(1))
        .withFuluEpoch(UInt64.valueOf(2))
        .withStubExecutionEngine()
        .withLogLevel("DEBUG");
  }
}
