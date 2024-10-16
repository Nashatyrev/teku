package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;

public interface ReqRespMethodLogger<TRequest, TResponse> {

  static <TRequest, TResponse> ReqRespMethodLogger<TRequest, TResponse> noop() {
    return new ReqRespMethodLogger<>() {
      @Override
      public ResponseLogger<TResponse> onInboundRequest(PeerId fromPeer, TRequest request) {
        return ResponseLogger.noop();
      }

      @Override
      public ResponseLogger<TResponse> onOutboundRequest(PeerId toPeer, TRequest request) {
        return ResponseLogger.noop();
      }
    };
  }

  interface ResponseLogger<TResponse> {

    static <TResponse> ResponseLogger<TResponse> noop() {
      return new ResponseLogger<>() {
        @Override
        public void onNextItem(TResponse s) {}

        @Override
        public void onComplete() {}

        @Override
        public void onError(Throwable error) {}
      };
    }

    void onNextItem(TResponse s);

    void onComplete();

    void onError(Throwable error);

  }

  class PeerId {
    public static PeerId fromNodeId(UInt256 nodeId) {
      return new PeerId(nodeId, Optional.empty());
    }
    public static PeerId fromPeerAndNodeId(String base58PeerId, UInt256 nodeId) {
      return new PeerId(nodeId, Optional.of(base58PeerId));
    }

    private final UInt256 nodeId;
    private final Optional<String> base58PeerId;

    public PeerId(UInt256 nodeId, Optional<String> base58PeerId) {
      this.nodeId = nodeId;
      this.base58PeerId = base58PeerId;
    }

    @Override
    public String toString() {
      String sNodeId = nodeId.toHexString();
      String sShortNodeId =
          sNodeId.substring(0, 10) + "..." + sNodeId.substring(sNodeId.length() - 8);
      return base58PeerId.map(s -> s + " (nodeId = " + sShortNodeId + ")").orElse(sShortNodeId);
    }
  }

  ResponseLogger<TResponse> onInboundRequest(PeerId fromPeer, TRequest request);

  ResponseLogger<TResponse> onOutboundRequest(PeerId toPeer, TRequest request);
}
