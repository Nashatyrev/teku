package tech.pegasys.teku.das.log.parser

import kotlinx.datetime.Instant
import org.apache.tuweni.bytes.Bytes32
import tech.pegasys.teku.das.log.parser.CommonPatterns.Companion.BYTES32_PATTERN
import tech.pegasys.teku.das.log.parser.CommonPatterns.Companion.INT_PATTERN
import tech.pegasys.teku.infrastructure.unsigned.UInt64

class BlockSlotRootParser : LogParser<BlockSlotRootParser.BlockSlotRoot> {

    data class BlockSlotRoot(
        override val time: Instant,
        val slot: UInt64,
        val root: Bytes32
    ) : LogEvent

    private val reg1 = Regex("""block ($INT_PATTERN) \(($BYTES32_PATTERN)\)""")
    private val reg2 = Regex("""headBlockRoot=($BYTES32_PATTERN), headBlockSlot=($INT_PATTERN)""")

    override fun parseLogEntry(s: String): BlockSlotRoot? {
        reg1.find(s)?.also {
            return BlockSlotRoot(
                parserContext.parseTime(s)!!,
                UInt64.valueOf(INT_PATTERN.parser(it.groupValues[1]).toLong()),
                BYTES32_PATTERN.parser(it.groupValues[2])

            )
        }

        reg2.find(s)?.also {
            return BlockSlotRoot(
                parserContext.parseTime(s)!!,
                UInt64.valueOf(INT_PATTERN.parser(it.groupValues[2]).toLong()),
                BYTES32_PATTERN.parser(it.groupValues[1])

            )
        }
        return null
    }
}