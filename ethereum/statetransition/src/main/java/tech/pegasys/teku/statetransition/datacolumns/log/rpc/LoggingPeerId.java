package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import org.apache.tuweni.units.bigints.UInt256;

import java.util.Optional;

public class LoggingPeerId {
  public static LoggingPeerId fromNodeId(UInt256 nodeId) {
    return new LoggingPeerId(nodeId, Optional.empty());
  }

  public static LoggingPeerId fromPeerAndNodeId(String base58PeerId, UInt256 nodeId) {
    return new LoggingPeerId(nodeId, Optional.of(base58PeerId));
  }

  private final UInt256 nodeId;
  private final Optional<String> base58PeerId;

  public LoggingPeerId(UInt256 nodeId, Optional<String> base58PeerId) {
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
