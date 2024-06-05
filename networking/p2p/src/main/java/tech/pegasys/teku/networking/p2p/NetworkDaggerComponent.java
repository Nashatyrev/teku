package tech.pegasys.teku.networking.p2p;

import dagger.Component;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkDaggerModule;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5DaggerModule;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkModule;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipNetworkDaggerModule;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipRouterDaggerModule;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    ExternalsDaggerModule.class,
    GossipRouterDaggerModule.class,
    GossipNetworkDaggerModule.class,
    DiscV5DaggerModule.class,
    DiscoveryNetworkDaggerModule.class,
    LibP2PNetworkModule.class,
    NetworkDaggerModule.class
})
public interface NetworkDaggerComponent {

  DiscoveryNetwork<?> discoveryNetwork();
}
