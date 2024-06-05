package tech.pegasys.teku.networking.p2p;

import dagger.Module;
import dagger.Provides;
import java.util.List;
import javax.inject.Singleton;
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
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

@Module
public class ExternalsDaggerModule {

  protected AsyncRunner asyncRunner;
  protected NetworkingSpecConfig networkingSpecConfig;
  protected LibP2PNetwork.PrivateKeyProvider privateKeyProvider;
  protected ReputationManager reputationManager;
  protected MetricsSystem metricsSystem;
  protected List<RpcMethod<?, ?, ?>> rpcMethods;
  protected List<PeerHandler> peerHandlers;
  protected PreparedGossipMessageFactory preparedGossipMessageFactory;
  protected GossipTopicFilter gossipTopicFilter;
  protected TimeProvider timeProvider;
  protected KeyValueStore<String, Bytes> kvStore;
  protected P2PNetwork<?> p2pNetwork;
  protected PeerPools peerPools;
  protected PeerSelectionStrategy peerSelectionStrategy;
  protected DiscoveryConfig discoveryConfig;
  protected NetworkConfig p2pConfig;
  protected Spec spec;
  protected SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;

  private final GossipConfig gossipConfig;
  private final PreparedGossipMessageFactory defaultMessageFactory;

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
  DiscoveryConfig provideDiscoveryConfig() {
    return discoveryConfig;
  }

  @Provides
  @Singleton
  NetworkConfig provideNetworkConfig() {
    return p2pConfig;
  }

  @Provides
  @Singleton
  KeyValueStore<String, Bytes> provideKVStore() {
    return kvStore;
  }

  @Provides
  @Singleton
  Spec provideSpec() {
    return spec;
  }

  @Provides
  @Singleton
  SchemaDefinitionsSupplier provideCurrentSchemaDefinitionsSupplier() {
    return currentSchemaDefinitionsSupplier;
  }

  @Provides
  @Singleton
  List<RpcMethod<?, ?, ?>> provideRpcMethods() {
    return rpcMethods;
  }

  @Provides
  @Singleton
  LibP2PNetwork.PrivateKeyProvider privateKeyProvider() {
    return privateKeyProvider;
  }

  @Provides
  @Singleton
  ReputationManager provideReputationManager() {
    return reputationManager;
  }

  @Provides
  @Singleton
  List<PeerHandler> providePeerHandlers() {
    return peerHandlers;
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
    return asyncRunner;
  }

  @Provides
  @Singleton
  public PeerSelectionStrategy providePeerSelectionStrategy() {
    return peerSelectionStrategy;
  }

  @Provides
  @Singleton
  public PeerPools providePeerPools() {
    return peerPools;
  }


  // ***** SETTERS ******

  public ExternalsDaggerModule metricsSystem(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public ExternalsDaggerModule asyncRunner(AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public ExternalsDaggerModule kvStore(KeyValueStore<String, Bytes> kvStore) {
    this.kvStore = kvStore;
    return this;
  }

  public ExternalsDaggerModule p2pNetwork(P2PNetwork<?> p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
    return this;
  }

  public ExternalsDaggerModule peerPools(PeerPools peerPools) {
    this.peerPools = peerPools;
    return this;
  }

  public ExternalsDaggerModule peerSelectionStrategy(
      PeerSelectionStrategy peerSelectionStrategy) {
    this.peerSelectionStrategy = peerSelectionStrategy;
    return this;
  }

  public ExternalsDaggerModule discoveryConfig(DiscoveryConfig discoveryConfig) {
    this.discoveryConfig = discoveryConfig;
    return this;
  }

  public ExternalsDaggerModule p2pConfig(NetworkConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    return this;
  }

  public ExternalsDaggerModule spec(Spec spec) {
    this.spec = spec;
    return this;
  }

  public ExternalsDaggerModule currentSchemaDefinitionsSupplier(
      SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    return this;
  }

  public ExternalsDaggerModule networkingSpecConfig(
      final NetworkingSpecConfig networkingSpecConfig) {
    this.networkingSpecConfig = networkingSpecConfig;
    return this;
  }

  public ExternalsDaggerModule privateKeyProvider(LibP2PNetwork.PrivateKeyProvider privateKeyProvider) {
    this.privateKeyProvider = privateKeyProvider;
    return this;
  }

  public ExternalsDaggerModule reputationManager(ReputationManager reputationManager) {
    this.reputationManager = reputationManager;
    return this;
  }

  public ExternalsDaggerModule rpcMethods(List<RpcMethod<?, ?, ?>> rpcMethods) {
    this.rpcMethods = rpcMethods;
    return this;
  }

  public ExternalsDaggerModule peerHandlers(List<PeerHandler> peerHandlers) {
    this.peerHandlers = peerHandlers;
    return this;
  }

  public ExternalsDaggerModule preparedGossipMessageFactory(
      PreparedGossipMessageFactory preparedGossipMessageFactory) {
    this.preparedGossipMessageFactory = preparedGossipMessageFactory;
    return this;
  }

  public ExternalsDaggerModule gossipTopicFilter(GossipTopicFilter gossipTopicFilter) {
    this.gossipTopicFilter = gossipTopicFilter;
    return this;
  }

  public ExternalsDaggerModule timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }
}
