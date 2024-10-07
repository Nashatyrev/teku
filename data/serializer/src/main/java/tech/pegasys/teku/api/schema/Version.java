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

package tech.pegasys.teku.api.schema;

import tech.pegasys.teku.spec.SpecMilestone;

@SuppressWarnings("JavaCase")
public enum Version {
  phase0,
  altair,
  bellatrix,
  capella,
  deneb,
  eip7594;

  public static Version fromMilestone(final SpecMilestone milestone) {
    return switch (milestone) {
      case PHASE0 -> phase0;
      case ALTAIR -> altair;
      case BELLATRIX -> bellatrix;
      case CAPELLA -> capella;
      case DENEB -> deneb;
      // FIXME: hack for devnet
      case ELECTRA -> deneb;
    };
  }
}
