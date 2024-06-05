package tech.pegasys.teku.networking.p2p;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
public @interface DaggerQualifier {

  enum P2PDependency {
    LocalNodeDiscoveryPrivateKey,
    LocalNodeLibp2pPrivateKey,
    LocalNodeLibp2pPublicKey,
    LocalNodeLibp2pPeerId,
    LocalNodeRecord,
    Bootnodes,
    AdvertisedMultiaddr,
    ListenMultiaddr,
    DefaultLibp2pProtocols
  }

  P2PDependency value();
}
