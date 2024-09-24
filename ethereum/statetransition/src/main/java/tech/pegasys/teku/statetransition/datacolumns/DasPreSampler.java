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

import java.util.Collection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

public class DasPreSampler {

  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataAvailabilitySampler sampler;

  public DasPreSampler(DataAvailabilitySampler sampler) {
    this.sampler = sampler;
  }

  public void onNewPreImportBlocks(Collection<SignedBeaconBlock> blocks) {
    LOG.info(
        "DasPreSampler: requesting pre-sample for blocks: {}",
        () ->
            StringifyUtil.toIntRangeString(
                blocks.stream().map(block -> block.getSlot().intValue()).toList()));

    blocks.forEach(this::onNewPreImportBlock);
  }

  private void onNewPreImportBlock(SignedBeaconBlock block) {
    sampler
        .checkDataAvailability(block.getSlot(), block.getRoot(), block.getParentRoot())
        .finish(
            succ ->
                LOG.debug(
                    "DasPreSampler: success pre-sampling block {} ({})",
                    block.getSlot(),
                    block.getRoot()),
            err ->
                LOG.info(
                    "DasPreSampler: error pre-sampling block {} ({}): {}",
                    block.getSlot(),
                    block.getRoot(),
                    err));
  }
}
