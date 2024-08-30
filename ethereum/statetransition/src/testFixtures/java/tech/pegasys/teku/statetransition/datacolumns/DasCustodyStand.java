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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("unused")
public class DasCustodyStand {

  public static Builder builder(Spec spec) {
    return new Builder().withSpec(spec);
  }

  public final Spec spec;

  public final CanonicalBlockResolverStub blockResolver;
  public final UInt256 myNodeId;

  public final DataColumnSidecarDBStub db = new DataColumnSidecarDBStub();

  public final SpecConfigEip7594 config;
  public final DataColumnSidecarCustodyImpl custody;

  public final DataStructureUtil dataStructureUtil;

  private final List<SlotEventsChannel> slotListeners = new CopyOnWriteArrayList<>();
  private final List<FinalizedCheckpointChannel> finalizedListeners = new CopyOnWriteArrayList<>();

  public DasCustodyStand(
      Spec spec, UInt64 currentSlot, UInt256 myNodeId, Integer totalCustodySubnetCount) {
    this.spec = spec;
    this.myNodeId = myNodeId;
    this.blockResolver = new CanonicalBlockResolverStub(spec);
    this.config = SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
    this.custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            blockResolver,
            db,
            myNodeId,
            totalCustodySubnetCount);
    subscribeToSlotEvents(this.custody);
    subscribeToFinalizedEvents(this.custody);

    this.dataStructureUtil = new DataStructureUtil(0, spec);
  }

  public SignedBeaconBlock createBlockWithBlobs(int slot) {
    return createBlock(slot, 3);
  }

  public SignedBeaconBlock createBlockWithoutBlobs(int slot) {
    return createBlock(slot, 0);
  }

  public SignedBeaconBlock createBlock(int slot, int blobCount) {
    UInt64 slotU = UInt64.valueOf(slot);
    BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(blobCount);
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    return dataStructureUtil.signedBlock(block);
  }

  public DataColumnSidecar createSidecar(SignedBeaconBlock block, int column) {
    return dataStructureUtil.randomDataColumnSidecar(block.asHeader(), UInt64.valueOf(column));
  }

  public void subscribeToSlotEvents(SlotEventsChannel subscriber) {
    slotListeners.add(subscriber);
  }

  public void setSlot(int slot) {
    slotListeners.forEach(l -> l.onSlot(UInt64.valueOf(slot)));
  }

  public void subscribeToFinalizedEvents(FinalizedCheckpointChannel subscriber) {
    finalizedListeners.add(subscriber);
  }

  public void setFinalizedEpoch(int epoch) {
    Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.valueOf(epoch), Bytes32.ZERO);
    finalizedListeners.forEach(l -> l.onNewFinalizedCheckpoint(finalizedCheckpoint, false));
  }

  public static class Builder {
    private Spec spec;
    private UInt64 currentSlot = UInt64.ZERO;
    private UInt256 myNodeId = UInt256.ONE;
    private Integer totalCustodySubnetCount;

    public Builder withSpec(Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder withCurrentSlot(UInt64 currentSlot) {
      this.currentSlot = currentSlot;
      return this;
    }

    public Builder withMyNodeId(UInt256 myNodeId) {
      this.myNodeId = myNodeId;
      return this;
    }

    public Builder withTotalCustodySubnetCount(Integer totalCustodySubnetCount) {
      this.totalCustodySubnetCount = totalCustodySubnetCount;
      return this;
    }

    public DasCustodyStand build() {
      if (totalCustodySubnetCount == null) {
        checkNotNull(spec);
        SpecConfigEip7594 configEip7594 =
            SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
        totalCustodySubnetCount = configEip7594.getCustodyRequirement();
      }
      return new DasCustodyStand(spec, currentSlot, myNodeId, totalCustodySubnetCount);
    }
  }
}
