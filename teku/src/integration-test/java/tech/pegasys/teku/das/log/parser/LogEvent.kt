package tech.pegasys.teku.das.log.parser

import kotlinx.datetime.Instant

interface LogEvent {
    val time: Instant
}