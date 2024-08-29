package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.time.Duration;

public class DasCustodySampler implements DataAvailabilitySampler {

  public DasCustodySampler(AsyncRunner asyncRunner, UpdatableDataColumnSidecarCustody custody) {
    new DasLongPollCustody(custody, asyncRunner, Duration.ofDays(9));
  }

  @Override
  public SafeFuture<Void> checkDataAvailability(UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot) {
    return null;
  }
}
