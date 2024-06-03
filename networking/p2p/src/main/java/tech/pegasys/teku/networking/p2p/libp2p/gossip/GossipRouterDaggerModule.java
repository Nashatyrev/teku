package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import com.google.common.base.Preconditions;
import dagger.Module;
import dagger.Provides;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.MaxCountTopicSubscriptionFilter;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.PubsubProtocol;
import io.libp2p.pubsub.SeenCache;
import io.libp2p.pubsub.TTLSeenCache;
import io.libp2p.pubsub.TopicSubscriptionFilter;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.GossipScoreParams;
import io.libp2p.pubsub.gossip.builders.GossipRouterBuilder;
import kotlin.jvm.functions.Function1;
import org.apache.tuweni.bytes.Bytes;
import pubsub.pb.Rpc;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;

import static tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory.MAX_SUBSCRIPTIONS_PER_MESSAGE;
import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork.STRICT_FIELDS_VALIDATOR;
import static tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder.MAX_SUBSCRIBED_TOPICS;

@Module
public interface GossipRouterDaggerModule {

  interface PubsubMessageFactory extends Function1<Rpc.Message, PubsubMessage> {};

  @Provides
  static GossipParams provideGossipParams(GossipConfig gossipConfig) {
    return LibP2PParamsFactory.createGossipParams(gossipConfig);
  }

  @Provides
  static GossipScoreParams provideGossipScoreParams(GossipConfig gossipConfig) {
    return LibP2PParamsFactory.createGossipScoreParams(gossipConfig.getScoringConfig());
  }

  @Provides
  static TopicSubscriptionFilter provideTopicSubscriptionFilter(
      GossipTopicFilter gossipTopicFilter) {
    return new MaxCountTopicSubscriptionFilter(
        MAX_SUBSCRIPTIONS_PER_MESSAGE, MAX_SUBSCRIBED_TOPICS, gossipTopicFilter::isRelevantTopic);
  }

  @Provides
  static GossipRouterBuilder provideGossipRouterBuilder() {
    return new GossipRouterBuilder();
  }

  @Provides
  static SeenCache<Optional<ValidationResult>> provideSeenCache(
      GossipParams gossipParams, GossipRouterBuilder routerBuilder) {
    return new TTLSeenCache<>(
        new FastIdSeenCache<>(msg -> Bytes.wrap(msg.messageSha256())),
        gossipParams.getSeenTTL(),
        routerBuilder.getCurrentTimeSuppluer());
  }

  @Provides
  static PubsubMessageFactory providePubsubMessageFactory(
      @Named("recordArrivalTime") Optional<TimeProvider> timeProvider,
      GossipTopicHandlers topicHandlers,
      PreparedGossipMessageFactory preparedGossipMessageFactory,
      NetworkingSpecConfig networkingSpecConfig
  ) {
    return msg -> {
      Preconditions.checkArgument(
          msg.getTopicIDsCount() == 1,
          "Unexpected number of topics for a single message: " + msg.getTopicIDsCount());
      final Optional<UInt64> arrivalTimestamp = timeProvider.map(TimeProvider::getTimeInMillis);
      final String topic = msg.getTopicIDs(0);
      final Bytes payload = Bytes.wrap(msg.getData().toByteArray());

      final PreparedGossipMessage preparedMessage =
          topicHandlers
              .getHandlerForTopic(topic)
              .map(handler -> handler.prepareMessage(payload, arrivalTimestamp))
              .orElse(
                  preparedGossipMessageFactory.create(
                      topic, payload, networkingSpecConfig, arrivalTimestamp));

      return new PreparedPubsubMessage(msg, preparedMessage);
    };
  }

  @Provides
  @Singleton
  static GossipRouter provideGossipRouter(
      GossipRouterBuilder routerBuilder,
      GossipParams gossipParams,
      GossipScoreParams scoreParams,
      TopicSubscriptionFilter subscriptionFilter,
      SeenCache<Optional<ValidationResult>> seenCache,
      PubsubMessageFactory pubsubMessageFactory) {

    routerBuilder.setParams(gossipParams);
    routerBuilder.setScoreParams(scoreParams);
    routerBuilder.setProtocol(PubsubProtocol.Gossip_V_1_1);
    routerBuilder.setSubscriptionTopicSubscriptionFilter(subscriptionFilter);
    routerBuilder.setSeenCache(seenCache);
    routerBuilder.setMessageFactory(pubsubMessageFactory);
    routerBuilder.setMessageValidator(STRICT_FIELDS_VALIDATOR);
    return routerBuilder.build();
  }
}
