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

package tech.pegasys.teku.spec.config.features;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public interface Eip7594 {

  static Eip7594 required(final SpecConfig specConfig) {
    return specConfig
        .getOptionalEip7594Config()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected spec config with EIP7594 feature but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  /** Should be ignored on soft-fork */
  Bytes4 getEip7594ForkVersion();

  UInt64 getEip7594FeatureEpoch();

  UInt64 getFieldElementsPerCell();

  UInt64 getFieldElementsPerExtBlob();

  /** DataColumnSidecar's */
  UInt64 getKzgCommitmentsInclusionProofDepth();

  int getNumberOfColumns();

  // networking
  int getDataColumnSidecarSubnetCount();

  int getCustodyRequirement();

  int getSamplesPerSlot();

  int getMinEpochsForDataColumnSidecarsRequests();

  int getMaxRequestDataColumnSidecars();
}
