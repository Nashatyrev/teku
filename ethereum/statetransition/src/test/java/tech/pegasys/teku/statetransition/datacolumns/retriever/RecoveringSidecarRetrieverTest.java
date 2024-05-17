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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolverStub;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;

public class RecoveringSidecarRetrieverTest {

  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final UInt256 myNodeId = UInt256.ONE;

  final SpecConfigEip7594 config =
      SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
  final MiscHelpersEip7594 miscHelpers =
      MiscHelpersEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  final KZG kzg = KZG.getInstance();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  public RecoveringSidecarRetrieverTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  private SignedBeaconBlockHeader createSigned(BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }

  private ColumnSlotAndIdentifier createId(BeaconBlock block, int colIdx) {
    return new ColumnSlotAndIdentifier(
        block.getSlot(), new DataColumnIdentifier(block.getRoot(), UInt64.valueOf(colIdx)));
  }

  @Test
  void sanityTest() throws Exception {
    int blobCount = 3;
    int columnsInDbCount = 3;

    DataColumnSidecarRetrieverStub delegateRetriever = new DataColumnSidecarRetrieverStub();
    RecoveringSidecarRetriever recoverRetrievr =
        new RecoveringSidecarRetriever(
            delegateRetriever,
            kzg,
            miscHelpers,
            blockResolver,
            db,
            new StubAsyncRunner(),
            Duration.ofSeconds(1));
    List<Bytes> blobs =
        Stream.generate(dataStructureUtil::randomBlob)
            .map(SszByteVectorImpl::getBytes)
            .limit(blobCount)
            .toList();
    BeaconBlock block = blockResolver.addBlock(10, blobCount);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecars(block, createSigned(block), blobs, kzg);

    List<Integer> dbColumnIndexes =
        IntStream.range(10, Integer.MAX_VALUE).limit(columnsInDbCount).boxed().toList();
    dbColumnIndexes.forEach(idx -> db.addSidecar(sidecars.get(idx)));

    ColumnSlotAndIdentifier id0 = createId(block, 0);
    ColumnSlotAndIdentifier id1 = createId(block, 1);
    SafeFuture<DataColumnSidecar> res0 = recoverRetrievr.retrieve(id0);
    SafeFuture<DataColumnSidecar> res1 = recoverRetrievr.retrieve(id1);

    assertThat(delegateRetriever.requests).hasSize(2);

    recoverRetrievr.maybeInitiateRecovery(id0, res0);
    assertThat(delegateRetriever.requests).hasSize(2 + columnCount - columnsInDbCount);

    recoverRetrievr.maybeInitiateRecovery(id1, res1);
    assertThat(delegateRetriever.requests).hasSize(2 + columnCount - columnsInDbCount);

    delegateRetriever.requests.stream()
        .skip(50)
        .limit(columnCount / 2 - columnsInDbCount)
        .forEach(
            req -> {
              req.promise()
                  .complete(sidecars.get(req.columnId().identifier().getIndex().intValue()));
            });

    assertThat(res0.get(1, TimeUnit.SECONDS)).isEqualTo(sidecars.get(0));
    assertThat(res1.get(1, TimeUnit.SECONDS)).isEqualTo(sidecars.get(1));
    assertThat(delegateRetriever.requests).allMatch(r -> r.promise().isDone());
  }
}
