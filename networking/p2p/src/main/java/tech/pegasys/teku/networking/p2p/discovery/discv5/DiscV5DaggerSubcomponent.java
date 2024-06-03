package tech.pegasys.teku.networking.p2p.discovery.discv5;

import dagger.Subcomponent;

import javax.inject.Singleton;

@Singleton
@Subcomponent(modules = DiscV5DaggerModule.class)
public interface DiscV5DaggerSubcomponent {

  DiscV5Service discV5Service();
}
