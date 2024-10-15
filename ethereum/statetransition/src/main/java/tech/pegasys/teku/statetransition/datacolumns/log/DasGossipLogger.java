package tech.pegasys.teku.statetransition.datacolumns.log;

import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

import java.util.Optional;

public interface DasGossipLogger {

  DasGossipLogger NOOP =
      new DasGossipLogger() {
        @Override
        public void onReceive(
            DataColumnSidecar sidecar, InternalValidationResult validationResult) {}

        @Override
        public void onPublish(DataColumnSidecar sidecar, Optional<Throwable> result) {}

        @Override
        public void onDataColumnSubnetSubscribe(int subnetId) {}

        @Override
        public void onDataColumnSubnetUnsubscribe(int subnetId) {}
      };

  void onReceive(DataColumnSidecar sidecar, InternalValidationResult validationResult);

  void onPublish(DataColumnSidecar sidecar, Optional<Throwable> result);

  void onDataColumnSubnetSubscribe(int subnetId);

  void onDataColumnSubnetUnsubscribe(int subnetId);
}
