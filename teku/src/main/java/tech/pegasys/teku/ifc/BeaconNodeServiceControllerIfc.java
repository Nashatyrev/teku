package tech.pegasys.teku.ifc;

import tech.pegasys.teku.services.BeaconNodeServiceController;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;

public interface BeaconNodeServiceControllerIfc extends ServiceControllerIfc {

    default BeaconChainService getBeaconChainService() {
        return getServices().stream().filter(s -> s instanceof BeaconChainService).map(s -> (BeaconChainService) s).findFirst().orElseThrow();
    }

    default StorageService getStorageService() {
        return getServices().stream().filter(s -> s instanceof StorageService).map(s -> (StorageService) s).findFirst().orElseThrow();
    }
}
