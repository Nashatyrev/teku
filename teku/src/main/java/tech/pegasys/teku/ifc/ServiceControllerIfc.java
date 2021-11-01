package tech.pegasys.teku.ifc;

import tech.pegasys.teku.service.serviceutils.Service;

import java.util.List;

public interface ServiceControllerIfc {

    List<Service> getServices();
}
