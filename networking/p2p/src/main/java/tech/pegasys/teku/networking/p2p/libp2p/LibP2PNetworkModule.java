package tech.pegasys.teku.networking.p2p.libp2p;

import dagger.Module;
import dagger.Provides;
import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.dsl.Builder;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.etc.types.ByteArrayExtKt;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.Ping;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.p2p.DaggerQualifier;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkDaggerSubcomponent;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipNetworkDaggerSubcomponent;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;

import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.AdvertisedMultiaddr;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.DefaultLibp2pProtocols;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.ListenMultiaddr;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeId;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeLibp2pPrivateKey;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeLibp2pPublicKey;
import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_OPEN_STREAMS_RATE_LIMIT;
import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT;

@Module(
    subcomponents = {
      GossipNetworkDaggerSubcomponent.class,
      DiscoveryNetworkDaggerSubcomponent.class
    })
public interface LibP2PNetworkModule {

  @Provides
  List<RpcMethod<?, ?, ?>> provideRpcMethods();

  @Provides
  LibP2PNetwork.PrivateKeyProvider privateKeyProvider();

  @Provides
  ReputationManager provideReputationManager();

  @Provides
  List<PeerHandler> providePeerHandlers();

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeLibp2pPrivateKey)
  static PrivKey provideNodePrivateKey(LibP2PNetwork.PrivateKeyProvider privateKeyProvider) {
    return privateKeyProvider.get();
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeLibp2pPublicKey)
  static PubKey provideNodePublicKey(
      @DaggerQualifier(LocalNodeLibp2pPrivateKey) PrivKey privateKey) {
    return privateKey.publicKey();
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeId)
  static NodeId provideLocalNodeId(@DaggerQualifier(LocalNodeLibp2pPublicKey) PubKey nodePubKey) {
    return new LibP2PNodeId(PeerId.fromPubKey(nodePubKey));
  }

  @Provides
  static List<? extends RpcHandler<?, ?, ?>> provideRpcHandlers(
      AsyncRunner asyncRunner, List<RpcMethod<?, ?, ?>> rpcMethods) {
    return rpcMethods.stream().map(m -> new RpcHandler<>(asyncRunner, m)).toList();
  }

  @Provides
  @DaggerQualifier(AdvertisedMultiaddr)
  static Multiaddr provideAdvertisedMultiaddr(
      NetworkConfig networkConfig, @DaggerQualifier(LocalNodeId) NodeId nodeId) {
    return MultiaddrUtil.fromInetSocketAddress(
        new InetSocketAddress(networkConfig.getAdvertisedIp(), networkConfig.getAdvertisedPort()),
        nodeId);
  }

  @Provides
  @DaggerQualifier(ListenMultiaddr)
  static Multiaddr provideListenMultiaddr(NetworkConfig networkConfig) {
    return MultiaddrUtil.fromInetSocketAddress(
        new InetSocketAddress(networkConfig.getNetworkInterface(), networkConfig.getListenPort()));
  }

  @Provides
  @Singleton
  static Ping providePingProtocol() {
    return new Ping();
  }

  @Provides
  @Singleton
  static Identify provideIdentifyProtocol(
      @DaggerQualifier(LocalNodeLibp2pPublicKey) PubKey nodePubKey,
      @DaggerQualifier(AdvertisedMultiaddr) Multiaddr advertisedAddr,
      Gossip gossip,
      Ping ping) {

    IdentifyOuterClass.Identify identifyMsg =
        IdentifyOuterClass.Identify.newBuilder()
            .setProtocolVersion("ipfs/0.1.0")
            .setAgentVersion(VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.VERSION)
            .setPublicKey(ByteArrayExtKt.toProtobuf(nodePubKey.bytes()))
            .addListenAddrs(ByteArrayExtKt.toProtobuf(advertisedAddr.serialize()))
            .setObservedAddr(ByteArrayExtKt.toProtobuf(advertisedAddr.serialize()))
            .addAllProtocols(ping.getProtocolDescriptor().getAnnounceProtocols())
            .addAllProtocols(gossip.getProtocolDescriptor().getAnnounceProtocols())
            .build();
    return new Identify(identifyMsg);
  }

  @Provides
  @Singleton
  @DaggerQualifier(DefaultLibp2pProtocols)
  static List<ProtocolBinding<?>> provideDefaultLibp2pProtocols(
      Ping pingProtocol, Identify identifyProtocol) {
    return List.of(pingProtocol, identifyProtocol);
  }

  @Provides
  @Singleton
  static PeerManager providePeerManager(
      MetricsSystem metricsSystem,
      ReputationManager reputationManager,
      List<PeerHandler> peerHandlers,
      List<? extends RpcHandler<?, ?, ?>> rpcHandlers,
      Gossip gossip) {
    return new PeerManager(
        metricsSystem,
        reputationManager,
        peerHandlers,
        rpcHandlers,
        (peerId) -> gossip.getGossipScore(peerId));
  }

  @Provides
  static Firewall provideFirewall() {
    return new Firewall(Duration.ofSeconds(30));
  }

  @Provides
  static MuxFirewall provideMuxFirewall() {
    return new MuxFirewall(
        REMOTE_OPEN_STREAMS_RATE_LIMIT, REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT);
  }

  static Host createHost(
      NetworkConfig networkConfig,
      @DaggerQualifier(ListenMultiaddr) Multiaddr listenAddr,
      @DaggerQualifier(LocalNodeLibp2pPrivateKey) PrivKey privKey,
      Gossip gossip,
      PeerManager peerManager,
      Firewall firewall,
      MuxFirewall muxFirewall,
      List<? extends RpcHandler<?, ?, ?>> rpcHandlers,
      @DaggerQualifier(DefaultLibp2pProtocols) List<ProtocolBinding<?>> defaultLibp2pProtocols) {

    return BuilderJKt.hostJ(
        Builder.Defaults.None,
        b -> {
          b.getIdentity().setFactory(() -> privKey);
          b.getTransports().add(TcpTransport::new);
          b.getSecureChannels().add(NoiseXXSecureChannel::new);

          // yamux MUST take precedence during negotiation
          if (networkConfig.isYamuxEnabled()) {
            // TODO: https://github.com/Consensys/teku/issues/7532
            final int maxBufferedConnectionWrites = 150 * 1024 * 1024;
            b.getMuxers().add(StreamMuxerProtocol.getYamux(maxBufferedConnectionWrites));
          }
          b.getMuxers().add(StreamMuxerProtocol.getMplex());

          b.getNetwork().listen(listenAddr.toString());

          b.getProtocols().addAll(defaultLibp2pProtocols);
          b.getProtocols().add(gossip);
          b.getProtocols().addAll(rpcHandlers);

          if (networkConfig.getWireLogsConfig().isLogWireCipher()) {
            b.getDebug().getBeforeSecureHandler().addLogger(LogLevel.DEBUG, "wire.ciphered");
          }
          b.getDebug().getBeforeSecureHandler().addNettyHandler(firewall);

          if (networkConfig.getWireLogsConfig().isLogWirePlain()) {
            b.getDebug().getAfterSecureHandler().addLogger(LogLevel.DEBUG, "wire.plain");
          }
          if (networkConfig.getWireLogsConfig().isLogWireMuxFrames()) {
            b.getDebug().getMuxFramesHandler().addLogger(LogLevel.DEBUG, "wire.mux");
          }

          b.getConnectionHandlers().add(peerManager);

          b.getDebug().getMuxFramesHandler().addHandler(muxFirewall);
        });
  }

  static LibP2PNetwork provideLibp2pNetwork(
      @DaggerQualifier(LocalNodeLibp2pPrivateKey) PrivKey privKey,
      @DaggerQualifier(LocalNodeId) NodeId nodeId,
      Host host,
      PeerManager peerManager,
      @DaggerQualifier(AdvertisedMultiaddr) Multiaddr advertisedMultiaddr,
      GossipNetwork gossipNetwork,
      NetworkConfig networkConfig) {
    return new LibP2PNetwork(
        privKey,
        nodeId,
        host,
        peerManager,
        advertisedMultiaddr,
        gossipNetwork,
        networkConfig.getListenPort());
  }
}
