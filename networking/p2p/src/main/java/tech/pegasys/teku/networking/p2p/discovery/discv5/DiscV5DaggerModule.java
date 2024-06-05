package tech.pegasys.teku.networking.p2p.discovery.discv5;

import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.p2p.DaggerQualifier;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.Bootnodes;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeTuweniPrivateKey;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodePrivateKeyBytes;
import static tech.pegasys.teku.networking.p2p.DaggerQualifier.P2PDependency.LocalNodeRecord;

@Module
public interface DiscV5DaggerModule {

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeTuweniPrivateKey)
  static SECP256K1.SecretKey provideLocalNodePrivateKey(
      @DaggerQualifier(LocalNodePrivateKeyBytes) Bytes localNodePrivateKeyBytes) {
    return SecretKeyParser.fromLibP2pPrivKey(localNodePrivateKeyBytes);
  }

  @Provides
  @Singleton
  @DaggerQualifier(Bootnodes)
  static List<NodeRecord> provideBootnodes(DiscoveryConfig discoveryConfig) {
    return discoveryConfig.getBootnodes().stream().map(NodeRecordFactory.DEFAULT::fromEnr).toList();
  }

  @Provides
  static DiscoverySystemBuilder provideDiscoverySystemBuilder() {
    return DiscV5Service.createDefaultDiscoverySystemBuilder();
  }

  @Provides
  @Singleton
  static NodeRecordConverter provideNodeRecordConverter() {
    return new NodeRecordConverter();
  }

  @Provides
  static NodeRecordBuilder provideNodeRecordBuilder() {
    return new NodeRecordBuilder();
  }

  @Provides
  static NewAddressHandler provideNewAddressHandler(
      Optional<DiscoveryAdvertisedAddress> maybeDiscoveryAdvertisedAddress,
      @DaggerQualifier(LocalNodeTuweniPrivateKey) SECP256K1.SecretKey localNodePrivateKey) {
    return maybeDiscoveryAdvertisedAddress
        .map(
            advertiseAddress ->
                (NewAddressHandler)
                    (oldRecord, newAddress) ->
                        Optional.of(
                            oldRecord.withNewAddress(
                                newAddress,
                                Optional.of(advertiseAddress.tcpPort()),
                                localNodePrivateKey)))
        .orElse((oldRecord, newAddress) -> Optional.of(oldRecord));
  }

  @Provides
  static Optional<DiscoveryAdvertisedAddress> provideDiscoveryAdvertisedAddress(
      DiscoveryConfig discoConfig, NetworkConfig p2pConfig) {

    if (p2pConfig.hasUserExplicitlySetAdvertisedIp()) {
      return Optional.of(
          new DiscoveryAdvertisedAddress(
              p2pConfig.getAdvertisedIp(),
              p2pConfig.getAdvertisedPort(),
              discoConfig.getAdvertisedUdpPort()));
    } else {
      return Optional.empty();
    }
  }

  @Provides
  @Singleton
  @DaggerQualifier(LocalNodeRecord)
  static NodeRecord provideLocalNodeRecord(
      NodeRecordBuilder nodeRecordBuilder,
      @DaggerQualifier(LocalNodeTuweniPrivateKey) SECP256K1.SecretKey localNodePrivateKey,
      DiscV5SeqNoStore discV5SeqNoStore,
      Optional<DiscoveryAdvertisedAddress> maybeDiscoveryAdvertisedAddress) {

    UInt64 seqNo = discV5SeqNoStore.getSeqNo().orElse(UInt64.ZERO).add(1);

    nodeRecordBuilder.secretKey(localNodePrivateKey).seq(seqNo);

    maybeDiscoveryAdvertisedAddress.ifPresent(
        advertisedAddress ->
            nodeRecordBuilder.address(
                advertisedAddress.address(),
                advertisedAddress.tcpPort(),
                advertisedAddress.udpPort()));

    return nodeRecordBuilder.build();
  }

  @Provides
  @Singleton
  static DiscV5SeqNoStore provideDiscV5SeqNoStore(KeyValueStore<String, Bytes> kvStore) {
    return DiscV5SeqNoStore.createFromKVStore(kvStore);
  }

  @Provides
  @Singleton
  static NodeRecordListener provideNodeRecordListener(DiscV5SeqNoStore discV5SeqNoStore) {
    return (oldRecord, newRecord) -> discV5SeqNoStore.putSeqNo(newRecord.getSeq());
  }

  @Provides
  @Singleton
  static AddressAccessPolicy provideAddressAccessPolicy(DiscoveryConfig discoConfig) {
    return discoConfig.areSiteLocalAddressesEnabled()
        ? AddressAccessPolicy.ALLOW_ALL
        : address -> !address.getAddress().isSiteLocalAddress();
  }

  @Provides
  @Singleton
  static DiscoverySystem provideDiscoverySystem(
      DiscoveryConfig discoConfig,
      NetworkConfig p2pConfig,
      DiscoverySystemBuilder discoverySystemBuilder,
      @DaggerQualifier(LocalNodeTuweniPrivateKey) SECP256K1.SecretKey localNodePrivateKey,
      @DaggerQualifier(Bootnodes) List<NodeRecord> bootnodes,
      @DaggerQualifier(LocalNodeRecord) NodeRecord localNodeRecord,
      NodeRecordListener nodeRecordListener,
      NewAddressHandler newAddressHandler,
      AddressAccessPolicy addressAccessPolicy) {

    final String listenAddress = p2pConfig.getNetworkInterface();
    final int listenUdpPort = discoConfig.getListenUdpPort();

    return discoverySystemBuilder
        .listen(listenAddress, listenUdpPort)
        .secretKey(localNodePrivateKey)
        .bootnodes(bootnodes)
        .localNodeRecord(localNodeRecord)
        .newAddressHandler(newAddressHandler)
        .localNodeRecordListener(nodeRecordListener)
        .addressAccessPolicy(addressAccessPolicy)
        .build();
  }

  @Provides
  @Singleton
  static DiscV5Service provideDiscV5Service(
      final DiscoverySystem discoverySystem,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      @DaggerQualifier(Bootnodes) final List<NodeRecord> bootnodes,
      final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier,
      final NodeRecordConverter nodeRecordConverter) {

    return new DiscV5Service(
        discoverySystem,
        metricsSystem,
        asyncRunner,
        bootnodes,
        currentSchemaDefinitionsSupplier,
        nodeRecordConverter);
  }
}
