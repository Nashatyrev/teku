package tech.pegasys.teku.networking.p2p;

import dagger.Binds;
import dagger.Component;
import javax.inject.Singleton;

import dagger.Module;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkDaggerModule;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5DaggerModule;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkModule;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipNetworkDaggerModule;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipRouterDaggerModule;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;

@Module
public interface NetworkDaggerModule {

  @Binds
  P2PNetwork<? extends Peer> bindP2PNetwork(LibP2PNetwork libP2PNetwork);

  @Binds
  DiscoveryService bindDiscoveryService(DiscV5Service discV5Service);

}
