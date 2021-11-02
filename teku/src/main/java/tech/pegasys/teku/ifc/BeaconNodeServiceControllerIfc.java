/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ifc;

import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;

public interface BeaconNodeServiceControllerIfc extends ServiceControllerIfc {

  default BeaconChainService getBeaconChainService() {
    return getServices().stream()
        .filter(s -> s instanceof BeaconChainService)
        .map(s -> (BeaconChainService) s)
        .findFirst()
        .orElseThrow();
  }

  default StorageService getStorageService() {
    return getServices().stream()
        .filter(s -> s instanceof StorageService)
        .map(s -> (StorageService) s)
        .findFirst()
        .orElseThrow();
  }
}
