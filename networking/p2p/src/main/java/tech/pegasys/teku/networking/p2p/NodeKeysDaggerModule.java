package tech.pegasys.teku.networking.p2p;

import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeDiscoveryPrivateKey;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeLibp2pPeerId;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeLibp2pPrivateKey;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeLibp2pPublicKey;

import dagger.Module;
import dagger.Provides;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1;
import tech.pegasys.teku.networking.p2p.discovery.discv5.SecretKeyParser;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

@Module
public interface NodeKeysDaggerModule {

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeLibp2pPrivateKey)
  static PrivKey provideNodePrivateKey(LibP2PNetwork.PrivateKeyProvider privateKeyProvider) {
    return privateKeyProvider.get();
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeLibp2pPublicKey)
  static PubKey provideNodePublicKey(
      @DaggerQualifier(LocalNodeLibp2pPrivateKey) PrivKey privateKey) {
    return privateKey.publicKey();
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeLibp2pPeerId)
  static NodeId provideLocalNodeId(@DaggerQualifier(LocalNodeLibp2pPublicKey) PubKey nodePubKey) {
    return new LibP2PNodeId(PeerId.fromPubKey(nodePubKey));
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeDiscoveryPrivateKey)
  static SECP256K1.SecretKey provideLocalNodePrivateKey(
      @DaggerQualifier(LocalNodeLibp2pPrivateKey) PrivKey libp2pPrivKey) {
    Bytes localNodePrivateKeyBytes = Bytes.wrap(libp2pPrivKey.raw());
    return SecretKeyParser.fromLibP2pPrivKey(localNodePrivateKeyBytes);
  }
}
