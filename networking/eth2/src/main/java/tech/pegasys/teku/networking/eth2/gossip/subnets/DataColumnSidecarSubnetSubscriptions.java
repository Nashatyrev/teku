/*
 * Copyright Consensys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.DataColumnSidecarTopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.features.Eip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarSubnetSubscriptions extends CommitteeSubnetSubscriptions {

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final OperationProcessor<DataColumnSidecar> processor;
  private final ForkInfo forkInfo;
  private final UInt64 eip7594ActivationEpoch;
  private final UInt64 eip7594EndEpoch;
  private final int subnetCount;
  private final DataColumnSidecarSchema dataColumnSidecarSchema;
  private final DebugDataDumper debugDataDumper;

  public DataColumnSidecarSubnetSubscriptions(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final RecentChainData recentChainData,
      final OperationProcessor<DataColumnSidecar> processor,
      final DebugDataDumper debugDataDumper,
      final ForkInfo forkInfo,
      final UInt64 eip7594ActivationEpoch,
      final UInt64 eip7594EndEpoch) {
    super(gossipNetwork, gossipEncoding);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.processor = processor;
    this.debugDataDumper = debugDataDumper;
    this.forkInfo = forkInfo;
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.getHighestMilestone());
    this.dataColumnSidecarSchema =
        SchemaDefinitionsEip7594.required(specVersion.getSchemaDefinitions())
            .getDataColumnSidecarSchema();
    this.subnetCount = Eip7594.required(specVersion.getConfig()).getDataColumnSidecarSubnetCount();
    this.eip7594ActivationEpoch = eip7594ActivationEpoch;
    this.eip7594EndEpoch = eip7594EndEpoch;
  }

  public SafeFuture<?> gossip(final DataColumnSidecar sidecar) {
    int subnetId = computeSubnetForSidecar(sidecar);
    final String topic =
        GossipTopics.getDataColumnSidecarSubnetTopic(
            forkInfo.getForkDigest(spec), subnetId, gossipEncoding);
    return gossipNetwork.gossip(topic, gossipEncoding.encode(sidecar));
  }

  @VisibleForTesting
  Optional<TopicChannel> getChannel(final DataColumnSidecar sidecar) {
    int subnetId = computeSubnetForSidecar(sidecar);
    return getChannelForSubnet(subnetId);
  }

  @Override
  protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
    final String topicName = GossipTopicName.getDataColumnSidecarSubnetTopicName(subnetId);
    return DataColumnSidecarTopicHandler.createHandler(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        debugDataDumper,
        forkInfo,
        eip7594ActivationEpoch,
        eip7594EndEpoch,
        topicName,
        dataColumnSidecarSchema,
        subnetId);
  }

  private int computeSubnetForSidecar(final DataColumnSidecar sidecar) {
    return sidecar.getIndex().mod(subnetCount).intValue();
  }
}
