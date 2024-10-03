package tech.pegasys.teku.das.log.parser

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ParserContext(
    val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX")
) {

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

    companion object {
        val DEFAULT = ParserContext()
    }
}