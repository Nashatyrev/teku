package tech.pegasys.teku.networking.p2p.discovery;

import dagger.Subcomponent;

@Subcomponent(modules = DiscoveryNetworkDaggerModule.class)
public interface DiscoveryNetworkDaggerSubcomponent {

  DiscoveryNetwork<?> discoveryNetwork();
}
