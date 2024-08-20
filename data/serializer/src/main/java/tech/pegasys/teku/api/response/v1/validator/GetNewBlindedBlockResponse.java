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

package tech.pegasys.teku.api.response.v1.validator;

import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.schema.interfaces.UnsignedBlindedBlock;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetNewBlindedBlockResponse {
  public final Version version;
  public final UnsignedBlindedBlock data;

  public GetNewBlindedBlockResponse(final Version version, final UnsignedBlindedBlock data) {
    this.version = version;
    this.data = data;
  }

  public GetNewBlindedBlockResponse(
      final SpecMilestone milestone, final UnsignedBlindedBlock data) {
    this(Version.fromMilestone(milestone), data);
  }
}
