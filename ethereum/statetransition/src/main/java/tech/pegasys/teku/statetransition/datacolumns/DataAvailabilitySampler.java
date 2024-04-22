package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface DataAvailabilitySampler {

  SafeFuture<Void> checkDataAvailability(UInt64 slot, Bytes32 blockRoot);
}
