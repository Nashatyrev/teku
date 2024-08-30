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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetrieverStub;

@SuppressWarnings("JavaCase")
public class CustodySyncIntegrationTest {

  final Spec spec =
      TestSpecFactory.createMinimalEip7594(
          builder ->
              builder.eip7594Builder(
                  dasBuilder ->
                      dasBuilder
                          .dataColumnSidecarSubnetCount(4)
                          .numberOfColumns(8)
                          .custodyRequirement(2)
                          .minEpochsForDataColumnSidecarsRequests(64)));

  final DataColumnSidecarRetrieverStub retrieverStub = new DataColumnSidecarRetrieverStub();
  final DasCustodyStand custodyStand = DasCustodyStand.builder(spec).build();
  final int maxSyncRequests = 32;
  final int minSyncRequests = 8;
  final DasCustodySync dasCustodySync =
      new DasCustodySync(
          custodyStand.custody,
          retrieverStub,
          maxSyncRequests,
          minSyncRequests);

  @Test
  void sanityTest() {
    custodyStand.setSlot(0);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    printStats();

    custodyStand.setSlot(5);

    printStats();
    assertThat(retrieverStub.requests).isEmpty();

    SignedBeaconBlock block_1 = custodyStand.createBlockWithBlobs(1);
    custodyStand.blockResolver.addBlock(block_1.getMessage());
    custodyStand.setSlot(6);

    printStats();
    assertThat(retrieverStub.requests).isNotEmpty();

    custodyStand.setSlot(7);

    printStats();
  }

  @Test
  void syncFromScratchLimitNumberOfBlockQueries() {
    custodyStand.setSlot(1000);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    printStats();

    for (int i = 0; i < 16; i++) {
      custodyStand.setSlot(1000 + i);
    }

    printStats();
  }

  private void printStats() {
    System.out.println(
        "db: "
            + custodyStand.db.getDbReadCounter().getAndSet(0)
            + ", block: "
            + custodyStand.blockResolver.getBlockAccessCounter().getAndSet(0)
            + ", column requests: "
            + retrieverStub.requests.size());
  }
}
