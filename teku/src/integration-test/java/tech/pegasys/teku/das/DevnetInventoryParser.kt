package tech.pegasys.teku.das

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.ethereum.beacon.discovery.schema.NodeRecordFactory
import java.net.URI

fun main() {
    val pairs = DevnetInventoryParser().downloadInventory()
    pairs.ethereum_pairs.forEach { name, data ->
        println("$name: nodeId: ${data.consensus.nodeId}")
    }
}

class DevnetInventoryParser(
    val url: String = "https://config.peerdas-devnet-2.ethpandaops.io/api/v1/nodes/inventory"
) {

    @Serializable
    data class Consensus(
        val client: String,
        val image: String,
        val enr: String,
        val peer_id: String,
        val beacon_uri: String) {

        @Transient
        val nodeRecord = NodeRecordFactory.DEFAULT.fromEnr(enr)
        @Transient
        val nodeId = Bytes32.wrap(nodeRecord.nodeId)

    }

    @Serializable
    data class Execution(
        val client: String,
        val image: String,
        val enode: String,
        val rpc_uri: String
    )

    @Serializable
    data class Node(
        val consensus: Consensus,
        val execution: Execution
    )

    @Serializable
    data class EthereumPairs(
        val ethereum_pairs: Map<String, Node>
    ) {
        val entryByNodeId = ethereum_pairs.entries.associateBy { it.value.consensus.nodeId }
        fun getNameByNodeId(nodeId: Bytes32) = entryByNodeId[nodeId]?.key
        fun getNameByNodeIdLastBytes(nodeIdSuffix: Bytes) =
            ethereum_pairs.entries
                .firstOrNull { it.value.consensus.nodeId.slice(32 - nodeIdSuffix.size()) == nodeIdSuffix }
                ?.key

    }

    fun downloadInventory(): EthereumPairs {
        print("Downloading inventory.json...")
        val inventoryFromWeb = URI(url)
            .toURL()
            .openStream()
            .readAllBytes()
            .toString(Charsets.UTF_8)
        println(" OK")

        return Json.decodeFromString<EthereumPairs>(inventoryFromWeb)
    }
}