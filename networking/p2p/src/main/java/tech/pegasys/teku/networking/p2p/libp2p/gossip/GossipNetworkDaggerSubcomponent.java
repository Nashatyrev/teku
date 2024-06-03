package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import dagger.Subcomponent;
import io.libp2p.pubsub.gossip.Gossip;
import tech.pegasys.teku.networking.p2p.DaggerExternalsModule;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;

import javax.inject.Singleton;

@Singleton
@Subcomponent(modules = {
    DaggerExternalsModule.class,
    GossipNetworkDaggerModule.class
})
public interface GossipNetworkDaggerSubcomponent {

  GossipNetwork gossipNetworkComponent();

  Gossip gossipComponent();
}
