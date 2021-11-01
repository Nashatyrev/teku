package tech.pegasys.teku.ifc;

import tech.pegasys.teku.BeaconNode;
import tech.pegasys.teku.services.BeaconNodeServiceController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface BeaconNodeIfc {

    BeaconNodeServiceControllerIfc getServiceController();
}
