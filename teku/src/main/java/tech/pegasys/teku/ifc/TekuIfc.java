package tech.pegasys.teku.ifc;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface TekuIfc {

    SafeFuture<BeaconNodeIfc> getBeaconNode();

}
