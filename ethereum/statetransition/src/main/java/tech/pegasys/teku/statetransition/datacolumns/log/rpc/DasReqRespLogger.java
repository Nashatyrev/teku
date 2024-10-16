package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;

public interface DasReqRespLogger {

  static DasReqRespLogger create(TimeProvider timeProvider) {
    return new DasReqRespLoggerImpl(timeProvider);
  }

  DasReqRespLogger NOOP =
      new DasReqRespLogger() {
        @Override
        public ReqRespMethodLogger<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>
            getDataColumnSidecarsByRootLogger() {
                    return ReqRespMethodLogger.noop();
        }

        @Override
        public ReqRespMethodLogger<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>
            getDataColumnSidecarsByRangeLogger() {
          return ReqRespMethodLogger.noop();
        }
      };

  ReqRespMethodLogger<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar> getDataColumnSidecarsByRootLogger();

   ReqRespMethodLogger<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar> getDataColumnSidecarsByRangeLogger();
}
