package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork.NULL_SEQNO_GENERATOR;

import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import io.libp2p.core.pubsub.PubsubApi;
import io.libp2p.core.pubsub.PubsubApiKt;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.netty.handler.logging.LoggingHandler;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;

@Module(includes = {
    DaggerGossipRouterModule.class
})
public interface DaggerGossipNetworkModule {

  @Provides
  static GossipNetwork provideGossipNetwork(
      MetricsSystem metricsSystem,
      Gossip gossip,
      PubsubPublisherApi publisher,
      GossipTopicHandlers topicHandlers
  ) {
    return new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers);
  }


  @Provides
  static GossipTopicHandlers provideGossipTopicHandlers() {
    return new GossipTopicHandlers();
  }

  @Provides
  @Singleton
  static Gossip provideGossip(
      GossipRouter gossipRouter,
      PubsubApi pubsubApi,
      @Named("wire.gossip") Optional<LoggingHandler> maybeDebugLogger) {
    return new Gossip(gossipRouter, pubsubApi, maybeDebugLogger.orElse(null));
  }

  @Provides
  static PubsubApi providePubsubApi(GossipRouter gossipRouter) {
    return PubsubApiKt.createPubsubApi(gossipRouter);
  }

  @Provides
  static PubsubPublisherApi providePubsubPublisherApi(Gossip gossip) {
    return gossip.createPublisher(null, NULL_SEQNO_GENERATOR);
  }

  @BindsOptionalOf
  @Named("wire.gossip")
  LoggingHandler optionalDebugGossipHandler();

  @BindsOptionalOf
  @Named("recordArrivalTime")
  TimeProvider optionalRecordArrivalTimeProvider();
}
