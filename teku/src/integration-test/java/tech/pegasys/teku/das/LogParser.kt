package tech.pegasys.teku.das

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.cert.ocsp.Req
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun main() {
    val logFile = File("""C:\ws\teku\work.dir\das-devnet-2\data\logs\teku.log""")
    val logParser = LogParser()
    logFile.forEachLine {
        if (logParser.syncHeadSlotTracker.lastHeadSlot < 20000)
        logParser.onNewLogEntry(it)
    }
    logParser.outboundRpcTracker.printStats()
}

class LogParser {
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX")

    val blockTracker = BlockTracker()
    val nodes = DevnetInventoryParser().downloadInventory().also {
        println("Known nodes:")
        it.ethereum_pairs.forEach { name, data ->
            println("$name: nodeId: ${data.consensus.nodeId}")
        }
    }
    val missingColumnByRpcTracker = MissingColumnByRpcTracker()
    val outboundRpcTracker = OutboundRpcTracker()
    val syncHeadSlotTracker = SyncHeadSlotTracker()

    fun onNewLogEntry(s: String) {
        blockTracker.onNewLogEntry(s)
//        missingColumnByRpcTracker.onNewLogEntry(s)
        outboundRpcTracker.onNewLogEntry(s)
        syncHeadSlotTracker.onNewLogEntry(s)
    }

    fun parseTime(s: String): Instant? {
        try {
            val temporalAccessor = timeFormatter.parse(s.substringBefore('|').trim())
            val localDateTime = LocalDateTime.from(temporalAccessor)
            val offset = ZoneOffset.from(temporalAccessor)

            // Convert to Instant
            val instant = localDateTime.toInstant(offset).toKotlinInstant()
            return instant
        } catch (e: Exception) {
            return null
        }
    }

    class BlockTracker {
        val rootToSlot = mutableMapOf<Bytes32, Int>()
        val slotToRoots= mutableMapOf<Int, MutableList<Bytes32>>()

        val reg1 = Regex("""block (\d+) \((0x[0-9a-f]{64})\)""")
        val reg2 = Regex("""headBlockRoot=(0x[0-9a-f]{64}), headBlockSlot=(\d+)""")

        fun add(rootS: String, slotS: String) {
            val root = Bytes32.fromHexString(rootS)
            val slot = slotS.toInt()
            rootToSlot[root] = slot
            slotToRoots.computeIfAbsent(slot) { mutableListOf() } += root
        }

        fun onNewLogEntry(s: String) {
            reg1.find(s)?.also {
                add(it.groupValues[2], it.groupValues[1])
            }

            reg2.find(s)?.also {
                add(it.groupValues[1], it.groupValues[2])
            }
        }
    }

    class SyncHeadSlotTracker {
        var lastHeadSlot: Int = 0

        val pattern = Regex("""Syncing +\*\*\* Target slot: \d+, Head slot: (\d+)""")

        fun onNewLogEntry(s: String) {
            pattern.find(s)?.also {
                lastHeadSlot = it.groupValues[1].toInt()
            }
        }
    }

    data class MissingColumnByRpcEntry(val time: Instant, val nodeId: Bytes32, val blockRoot: Bytes32, val colIndex: Int)
    inner class MissingColumnByRpcTracker() {

        val reg =
            Regex("""Error requesting data column sidecar DataColumnIdentifier\{block_root=(0x[0-9a-f]{64}), index=(\d+)} from (0x[0-9a-f]{64})""")

        fun maybeEntry(s: String) =
            reg.find(s)?.let {
                MissingColumnByRpcEntry(
                    parseTime(s)!!,
                    Bytes32.fromHexString(it.groupValues[3]),
                    Bytes32.fromHexString(it.groupValues[1]),
                    it.groupValues[2].toInt()
                )
            }

        fun onNewLogEntry(s: String) {
            val maybeEntry = maybeEntry(s)
            if (maybeEntry != null) {
                val nodeName = nodes.getNameByNodeId(maybeEntry.nodeId) ?: "<${maybeEntry.nodeId}>"
                val slot = blockTracker.rootToSlot[maybeEntry.blockRoot]
                println("${maybeEntry.time}: $slot/${maybeEntry.colIndex} $nodeName")
            }
        }
    }

    inner class OutboundRpcTracker {
        inner class Request(val time: Instant, val hash: Int, val nodeIdSuffix: String, val requestCount: Int)
        inner class Entry(val request: Request, val time: Instant, val responseCount: Int)

        val pendingRequests = mutableMapOf<Int, Request>()
        val entries = mutableListOf<Entry>()

        val requestPattern = Regex("""\[nyota] Requesting batch of (\d+) from 0x\.\.\.([0-9a-f]+), hash=(-?\d+)""")
        val responsePattern = Regex("""\[nyota] Response batch of (\d+) from 0x\.\.\.([0-9a-f]+), hash=(-?\d+)""")

        fun onNewLogEntry(s: String) {
            requestPattern.find(s)?.also {
                val hash = it.groupValues[3].toInt()
                pendingRequests[hash] = Request(parseTime(s)!!, hash, it.groupValues[2], it.groupValues[1].toInt())
            }

            responsePattern.find(s)?.also {
                val hash = it.groupValues[3].toInt()
                val req = pendingRequests.remove(hash)
                if (req == null) {
                    println("No request for response: $s")
                } else {
                    entries += Entry(req, parseTime(s)!!, it.groupValues[1].toInt())
                }
            }
        }

        fun printStats() {
            println("OutboundRpcTracker stats:")
            println("Total requests: ${entries.size + pendingRequests.size}, unresponded: ${pendingRequests.size}")
            entries
                .groupBy { it.request.nodeIdSuffix }
                .mapKeys { nodes.getNameByNodeIdLastBytes(Bytes.fromHexStringLenient(it.key)) ?: "<${it.key}>" }
                .toSortedMap()
                .forEach { nodeName, entries ->
                    val requestedColumns = entries.sumOf { it.request.requestCount }
                    val respondedColumns = entries.sumOf { it.responseCount }
                    println("$nodeName: requests: ${entries.size}, columns: $respondedColumns/$requestedColumns")
                }
        }
    }

    @Test
    fun checkBlockTracker() {
        val logParser = LogParser()

        logParser.onNewLogEntry("aasdasd sdfsdfgsdg fghfghfgh")
        logParser.onNewLogEntry("""2024-10-02 18:35:21.202+04:00 | forkChoiceNotifier-async-0 | DEBUG | ForkChoiceNotifierImpl | internalForkChoiceUpdated forkChoiceState ForkChoiceState{headBlockRoot=0x5e035e2fd515cc248f3ac6cbbf5fd4c749c55c791ca435d2787b69c8d120fb48, headBlockSlot=39005, headExecutionBlockNumber=23053, headExecutionBlockHash=0xd08362bd1ad2afa1758d4a664512f57ba9db9aa6e13f79c4e9862500506d0c2c, safeExecutionBlockHash=0x3eb708ed37f0b21d517397b6b7311397966c197d1ee78a51daa6423e8ad14a78, finalizedExecutionBlockHash=0x28e91d7cad709cbf09172b6045858504e45142222e3204030b26e5191b846e30, isHeadOptimistic=false}""")
        logParser.onNewLogEntry("""2024-10-02 18:34:48.553+04:00 | beaconchain-async-1 | INFO  | das-nyota | checkDataAvailability(): got 0 (of 4) columns from custody (or received by Gossip) for block 39005 (0x5e035e2fd515cc248f3ac6cbbf5fd4c749c55c791ca435d2787b69c8d120fb49), columns: (len: 0) []""")

        assertThat(logParser.blockTracker.rootToSlot).hasSize(2)
        assertThat(logParser.blockTracker.slotToRoots).hasSize(1)
        assertThat(logParser.blockTracker.slotToRoots.values.first()).hasSize(2)
    }
}
