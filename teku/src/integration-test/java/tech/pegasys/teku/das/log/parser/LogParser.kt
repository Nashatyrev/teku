package tech.pegasys.teku.das.log.parser

interface LogParser<TEvent : LogEvent> {

    val parserContext: ParserContext get() = ParserContext.DEFAULT

    fun parseLogEntry(s: String): TEvent?
}