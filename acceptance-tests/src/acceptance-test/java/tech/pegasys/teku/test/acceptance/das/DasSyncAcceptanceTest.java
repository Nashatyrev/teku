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
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import kotlin.ranges.IntRange;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class DasSyncAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldSyncToNodeWithGreaterFinalizedEpoch() throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withDasExtraCustodySubnetCount(128 - 4)
                .build());

    primaryNode.start();
    UInt64 genesisTime = primaryNode.getGenesisTime();
    final TekuBeaconNode lateJoiningNode =
        createLateJoiningNode(primaryNode, genesisTime.intValue());
    primaryNode.waitForEpochAtOrAbove(4);

    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
    lateJoiningNode.waitForEpochAtOrAbove(6);
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    int epochSlots = primaryNode.getSpec().slotsPerEpoch(UInt64.ZERO);
    int endSlot = 6 * epochSlots;
    assertAllBlocksExistWithoutForks(
        primaryNode, IntStream.range(1, endSlot).mapToObj(UInt64::valueOf).toList());
    int firstFuluSlot = 2 * epochSlots;
    int totalColumns =
        IntStream.range(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(lateJoiningNode, slot))
            .mapToInt(i -> i)
            .sum();

    assertThat(totalColumns).isGreaterThan(0);
  }

  private void assertAllBlocksExistWithoutForks(TekuBeaconNode node, List<UInt64> slots)
      throws IOException {
    Bytes32 parentRoot = null;
    for (UInt64 slot : slots) {
      Optional<SignedBeaconBlock> block = node.getBlockAtSlot(slot);
      assertThat(block).withFailMessage("Block missing at slot {}", slot).isNotEmpty();
      if (parentRoot != null) {
        assertThat(block.get().getParentRoot()).isEqualTo(parentRoot);
      }
      parentRoot = block.get().getRoot();
    }
  }

  private int getAndAssertDasCustody(TekuBeaconNode node, UInt64 fuluSlot) {
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
        .withAltairEpoch(UInt64.valueOf(0))
        .withBellatrixEpoch(UInt64.valueOf(0))
        .withCapellaEpoch(UInt64.valueOf(0))
        .withDenebEpoch(UInt64.valueOf(0))
        .withElectraEpoch(UInt64.valueOf(1))
        .withFuluEpoch(UInt64.valueOf(2))
        .withStubExecutionEngine();
    // uncomment to debug
    //        .withLogLevel("DEBUG");
  }
}
