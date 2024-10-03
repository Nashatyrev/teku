package tech.pegasys.teku.das.log

data class NodeIdentifier(val nodeIdStr: String, val stringType: Type) {

    enum class Type {
        PeerIdBase58, NodeIdHex, NodeIdSuffixHex, IpAddress
    }
}