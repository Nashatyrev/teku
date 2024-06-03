package tech.pegasys.teku.networking.p2p.discovery;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5DaggerSubcomponent;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

@Module(subcomponents = DiscV5DaggerSubcomponent.class)
public interface DiscoveryNetworkDaggerModule {

  @Binds
  DiscoveryService bindDiscoveryService(DiscV5Service discV5Service);

  @Singleton
  @Provides
  static ConnectionManager provideConnectionManager(
      MetricsSystem metricsSystem,
      DiscoveryService discoveryService,
      AsyncRunner asyncRunner,
      P2PNetwork<? extends Peer> p2pNetwork,
      PeerSelectionStrategy peerSelectionStrategy,
      DiscoveryConfig discoveryConfig,
      PeerPools peerPools) {
    return new ConnectionManager(
        metricsSystem,
        discoveryService,
        asyncRunner,
        p2pNetwork,
        peerSelectionStrategy,
        discoveryConfig.getStaticPeers().stream().map(p2pNetwork::createPeerAddress).toList(),
        peerPools);
  }

  @Singleton
  @Provides
  static DiscoveryNetwork<?> provideDiscoveryNetwork(
      P2PNetwork<? extends Peer> p2pNetwork,
      DiscoveryService discoveryService,
      ConnectionManager connectionManager,
      Spec spec,
      SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    return new DiscoveryNetwork<>(
        p2pNetwork, discoveryService, connectionManager, spec, currentSchemaDefinitionsSupplier);
  }
}
