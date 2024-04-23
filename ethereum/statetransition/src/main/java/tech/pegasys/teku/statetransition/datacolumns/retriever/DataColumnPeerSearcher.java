package tech.pegasys.teku.statetransition.datacolumns.retriever;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface DataColumnPeerSearcher {

  PeerRequest requestPeers(UInt64 slot, UInt64 columnIndex);

  interface PeerRequest {

    void dispose();
  }
}
