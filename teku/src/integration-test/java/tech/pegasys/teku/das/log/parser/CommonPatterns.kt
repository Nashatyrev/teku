package tech.pegasys.teku.das.log.parser

import org.apache.tuweni.bytes.Bytes32

class CommonPatterns<T>(val regexp: String, val parser: (String) -> T) {

    override fun toString() = regexp

    companion object {
        val INT_PATTERN =
            CommonPatterns("-?\\d+") { it.toInt() }
        val BYTES32_PATTERN =
            CommonPatterns("0x[0-9a-f]{64}") { Bytes32.fromHexStringLenient(it) }
        val IPV4_PATTERN =
            CommonPatterns("""\d\d?\d?\.\d\d?\d?\.\d\d?\d?\.\d\d?\d?""") { it }
    }

}