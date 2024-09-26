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

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

public interface DataAvailabilitySampler {

  enum SamplingEligibilityStatus { TOO_OLD, NO_BLOBS, NO_EIP7594, NEED_SAMPLING }

  DataAvailabilitySampler NOOP =
      new DataAvailabilitySampler() {
        @Override
        public SafeFuture<List<DataColumnSidecar>> checkDataAvailability(
            UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SamplingEligibilityStatus checkSamplingEligibility(BeaconBlock block) {
          return SamplingEligibilityStatus.TOO_OLD;
        }
      };

  SafeFuture<List<DataColumnSidecar>> checkDataAvailability(
      UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot);

  SamplingEligibilityStatus checkSamplingEligibility(BeaconBlock block);
}
