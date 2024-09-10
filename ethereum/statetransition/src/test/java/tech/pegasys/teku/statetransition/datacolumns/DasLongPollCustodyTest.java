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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.db.DelayedDasDb;

@SuppressWarnings("JavaCase")
public class DasLongPollCustodyTest {
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final Duration dbDelay = Duration.ofMillis(5);
  final DataColumnSidecarDB delayedDb = new DelayedDasDb(db, stubAsyncRunner, dbDelay);
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(delayedDb).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final UInt256 myNodeId = UInt256.ONE;

  final SpecConfigEip7594 config =
      SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
  final int subnetCount = config.getDataColumnSidecarSubnetCount();

  final DataColumnSidecarCustodyImpl custodyImpl =
      new DataColumnSidecarCustodyImpl(
          spec,
          blockResolver,
          dbAccessor,
          MinCustodyPeriodSlotCalculator.createFromSpec(spec),
          myNodeId,
          subnetCount);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  private final Duration longPollTimeout = Duration.ofMillis(200);
  private final DasLongPollCustody custody =
      new DasLongPollCustody(custodyImpl, stubAsyncRunner, spec, longPollTimeout);

  private final BeaconBlock block10 = blockResolver.addBlock(10, true);
  private final DataColumnSidecar sidecar10_0 = createSidecar(block10, 0);
  private final DataColumnIdentifier columnId10_0 = DataColumnIdentifier.createFromSidecar(sidecar10_0);
  private final DataColumnSidecar sidecar10_1 = createSidecar(block10, 1);
  private final DataColumnIdentifier columnId10_1 = DataColumnIdentifier.createFromSidecar(sidecar10_1);


  private DataColumnSidecar createSidecar(BeaconBlock block, int column) {
    return dataStructureUtil.randomDataColumnSidecar(createSigned(block), UInt64.valueOf(column));
  }

  private SignedBeaconBlockHeader createSigned(BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }

  private void advanceTimeGradually(Duration delta) {
    for (int i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(Duration.ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  @Test
  void testLongPollingColumnRequest() throws Exception {
    SafeFuture<Optional<DataColumnSidecar>> fRet0 = custody.getCustodyDataColumnSidecar(columnId10_0);
    SafeFuture<Optional<DataColumnSidecar>> fRet0_1 =
        custody.getCustodyDataColumnSidecar(columnId10_0);
    SafeFuture<Optional<DataColumnSidecar>> fRet1 = custody.getCustodyDataColumnSidecar(columnId10_1);

    advanceTimeGradually(longPollTimeout.plus(dbDelay).minus(Duration.ofMillis(1)));

    assertThat(fRet0).isNotDone();
    assertThat(fRet0_1).isNotDone();
    assertThat(fRet1).isNotDone();

    custody.onNewValidatedDataColumnSidecar(sidecar10_0);

    assertThat(fRet0.get(1, TimeUnit.SECONDS)).contains(sidecar10_0);
    assertThat(fRet0_1.get(1, TimeUnit.SECONDS)).contains(sidecar10_0);
    assertThat(fRet1).isNotDone();

    advanceTimeGradually(Duration.ofMillis(2));

    assertThat(fRet0.get(1, TimeUnit.SECONDS)).contains(sidecar10_0);
    assertThat(fRet0_1.get(1, TimeUnit.SECONDS)).contains(sidecar10_0);
    assertThat(fRet1.get(1, TimeUnit.SECONDS)).isEmpty();
  }

  @Test
  void testPendingRequestIsExecutedOnAsyncDb() throws Exception {
    SafeFuture<Optional<DataColumnSidecar>> fRet0 = custody.getCustodyDataColumnSidecar(columnId10_0);
    advanceTimeGradually(Duration.ofMillis(1));
    // db query is not yet complete when the data is added
    custody.onNewValidatedDataColumnSidecar(sidecar10_0);

    advanceTimeGradually(dbDelay);
    assertThat(fRet0).isCompletedWithValue(Optional.ofNullable(sidecar10_0));
  }

  @Test
  void testOptionalEmptyIsReturnedOnTimeout() {
    SafeFuture<Optional<DataColumnSidecar>> fRet0 = custody.getCustodyDataColumnSidecar(columnId10_0);

    advanceTimeGradually(longPollTimeout.multipliedBy(2));

    assertThat(fRet0).isCompletedWithValue(Optional.empty());
  }
}
