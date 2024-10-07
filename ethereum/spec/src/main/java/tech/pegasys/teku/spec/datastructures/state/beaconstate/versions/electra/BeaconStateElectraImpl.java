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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594.MutableBeaconStateElectra;

public class BeaconStateElectraImpl
    extends AbstractBeaconState<
        tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594
            .MutableBeaconStateElectra>
    implements BeaconStateEip7594, BeaconStateCache, ValidatorStatsAltair {

  BeaconStateElectraImpl(
      final BeaconStateSchema<
              BeaconStateEip7594,
              tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594
                  .MutableBeaconStateElectra>
          schema) {
    super(schema);
  }

  BeaconStateElectraImpl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStateElectraImpl(
      final AbstractSszContainerSchema<? extends SszContainer> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaEip7594 getBeaconStateSchema() {
    return (BeaconStateSchemaEip7594) getSchema();
  }

  @Override
  public MutableBeaconStateElectra createWritableCopy() {
    return new MutableBeaconStateElectraImpl(this);
  }

  @Override
  protected void describeCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStateEip7594.describeCustomEip7594Fields(stringBuilder, this);
  }
}
