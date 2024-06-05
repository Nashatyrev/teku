package tech.pegasys.teku.networking.p2p;

import dagger.Module;
import javax.inject.Singleton;

import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

import java.util.List;

import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodePrivateKeyBytes;

@Module
public class ExternalsDaggerModule {

  private final MetricsSystem metricsSystem;
  private final GossipConfig gossipConfig;
  private final NetworkingSpecConfig networkingSpecConfig;
  private final PreparedGossipMessageFactory defaultMessageFactory;
  private final GossipTopicFilter gossipTopicFilter;
  private final TimeProvider timeProvider;

  public ExternalsDaggerModule(
      MetricsSystem metricsSystem,
      GossipConfig gossipConfig,
      NetworkingSpecConfig networkingSpecConfig,
      PreparedGossipMessageFactory defaultMessageFactory,
      GossipTopicFilter gossipTopicFilter,
      TimeProvider timeProvider) {
    this.metricsSystem = metricsSystem;
    this.gossipConfig = gossipConfig;
    this.networkingSpecConfig = networkingSpecConfig;
    this.defaultMessageFactory = defaultMessageFactory;
    this.gossipTopicFilter = gossipTopicFilter;
    this.timeProvider = timeProvider;
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodePrivateKeyBytes)
  Bytes provideLocalNodePrivateKeyBytes() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  DiscoveryConfig provideDiscoveryConfig() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  NetworkConfig provideNetworkConfig() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  KeyValueStore<String, Bytes> provideKVStore() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  Spec provideSpec() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  SchemaDefinitionsSupplier provideCurrentSchemaDefinitionsSupplier() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  List<RpcMethod<?, ?, ?>> provideRpcMethods() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  LibP2PNetwork.PrivateKeyProvider privateKeyProvider() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  ReputationManager provideReputationManager() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  List<PeerHandler> providePeerHandlers() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  public MetricsSystem provideMetricsSystem() {
    return metricsSystem;
  }

  @Provides
  @Singleton
  public GossipConfig provideGossipConfig() {
    return gossipConfig;
  }

  @Provides
  @Singleton
  public NetworkingSpecConfig provideNetworkingSpecConfig() {
    return networkingSpecConfig;
  }

  @Provides
  @Singleton
  public PreparedGossipMessageFactory provideDefaultMessageFactory() {
    return defaultMessageFactory;
  }

  @Provides
  @Singleton
  public GossipTopicFilter provideGossipTopicFilter() {
    return gossipTopicFilter;
  }

  @Provides
  @Singleton
  public TimeProvider provideTimeProvider() {
    return timeProvider;
  }

  @Provides
  @Singleton
  public AsyncRunner provideAsyncRunner() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  public PeerSelectionStrategy providePeerSelectionStrategy() {
    throw new UnsupportedOperationException("TODO");
  }

  @Provides
  @Singleton
  public PeerPools providePeerPools() {
    throw new UnsupportedOperationException("TODO");
  }
}
