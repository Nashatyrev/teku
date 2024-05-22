package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import dagger.Component;
import io.libp2p.pubsub.gossip.Gossip;
import tech.pegasys.teku.networking.p2p.DaggerExternalsModule;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
    DaggerExternalsModule.class,
    DaggerGossipNetworkModule.class
})
public interface DaggerGossipNetworkComponent {

  GossipNetwork gossipNetworkComponent();

  Gossip gossipComponent();
}
