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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigFuluImpl;

public class FuluBuilder implements ForkConfigBuilder<SpecConfigElectra, SpecConfigFulu> {

  private Bytes4 fuluForkVersion;
  private UInt64 fuluForkEpoch;

  FuluBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigFulu> build(
      final SpecConfigAndParent<SpecConfigElectra> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigFuluImpl(specConfigAndParent.specConfig(), fuluForkVersion, fuluForkEpoch),
        specConfigAndParent);
  }

  public FuluBuilder fuluForkEpoch(final UInt64 fuluForkEpoch) {
    checkNotNull(fuluForkEpoch);
    this.fuluForkEpoch = fuluForkEpoch;
    return this;
  }

  public FuluBuilder fuluForkVersion(final Bytes4 fuluForkVersion) {
    checkNotNull(fuluForkVersion);
    this.fuluForkVersion = fuluForkVersion;
    return this;
  }

  @Override
  public void validate() {
    if (fuluForkEpoch == null) {
      fuluForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      fuluForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (fuluForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("fuluForkEpoch", fuluForkEpoch);
    constants.put("fuluForkVersion", fuluForkVersion);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("FULU_FORK_EPOCH", fuluForkEpoch);
  }
}
