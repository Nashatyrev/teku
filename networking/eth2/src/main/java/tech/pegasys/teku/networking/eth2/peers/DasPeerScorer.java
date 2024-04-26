package tech.pegasys.teku.networking.eth2.peers;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface DasPeerScorer extends PeerScorer {

  void addSamplingQuery(UInt64 slot, int dataColumnSubnetIndex);

  void removeSamplingQuery(UInt64 slot, int dataColumnSubnetIndex);

}
