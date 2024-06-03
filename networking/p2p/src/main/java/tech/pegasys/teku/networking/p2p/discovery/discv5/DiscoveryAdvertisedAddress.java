package tech.pegasys.teku.networking.p2p.discovery.discv5;

public record DiscoveryAdvertisedAddress(
    String address,
    int tcpPort,
    int udpPort
) {}
