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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DasSyncAcceptanceTest extends AcceptanceTestBase {

  private final int subnetCount = 128;
  private final int defaultCustodySubnetCount = 4;
  private final int fuluEpoch = 2;

  @Test
  public void shouldSyncToNodeWithGreaterFinalizedEpoch() throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withDasExtraCustodySubnetCount(subnetCount - defaultCustodySubnetCount)
                .build());

    primaryNode.start();
    UInt64 genesisTime = primaryNode.getGenesisTime();
    final TekuBeaconNode lateJoiningNode =
        createLateJoiningNode(primaryNode, genesisTime.intValue());
    primaryNode.waitForEpochAtOrAbove(fuluEpoch + 1);

    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
    lateJoiningNode.waitForEpochAtOrAbove(6);
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    int epochSlots = primaryNode.getSpec().slotsPerEpoch(UInt64.ZERO);
    int endSlot = 6 * epochSlots;
    assertAllBlocksExistWithoutForks(
        primaryNode, IntStream.range(1, endSlot).mapToObj(UInt64::valueOf).toList());
    int firstFuluSlot = fuluEpoch * epochSlots;
    int totalColumns =
        IntStream.range(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(lateJoiningNode, slot))
            .mapToInt(i -> i)
            .sum();

    assertThat(totalColumns).isNegative();
  }

  private void assertAllBlocksExistWithoutForks(final TekuBeaconNode node, final List<UInt64> slots)
      throws IOException {
    Bytes32 parentRoot = null;
    for (UInt64 slot : slots) {
      Optional<SignedBeaconBlock> block = node.getBlockAtSlot(slot);
      if (block.isPresent()) {
        if (parentRoot != null) {
          assertThat(block.get().getParentRoot()).isEqualTo(parentRoot);
        }
        parentRoot = block.get().getRoot();
      }
    }
  }

  private int getAndAssertDasCustody(final TekuBeaconNode node, final UInt64 fuluSlot) {
    try {
      Optional<SignedBeaconBlock> maybeBlock = node.getBlockAtSlot(fuluSlot);
      if (maybeBlock.isPresent()) {
        SignedBeaconBlock block = maybeBlock.get();
        boolean hasBlobs =
            !block
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .toVersionDeneb()
                .orElseThrow()
                .getBlobKzgCommitments()
                .isEmpty();
        int columnCount = node.getDataColumnSidecarCount(block.getRoot().toHexString());
        if (hasBlobs) {
          assertThat(columnCount).isNotZero();
        } else {
          assertThat(columnCount).isZero();
        }
        return columnCount;
      } else {
        return 0;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
        .withNetwork("minimal")
        .withAltairEpoch(UInt64.valueOf(0))
        .withBellatrixEpoch(UInt64.valueOf(0))
        .withCapellaEpoch(UInt64.valueOf(0))
        .withDenebEpoch(UInt64.valueOf(0))
        .withElectraEpoch(UInt64.valueOf(1))
        .withFuluEpoch(UInt64.valueOf(fuluEpoch))
        .withStubExecutionEngine()
        // uncomment to debug
        .withLogLevel("DEBUG");
  }
}
