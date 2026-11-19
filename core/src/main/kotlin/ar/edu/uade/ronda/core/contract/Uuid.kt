package ar.edu.uade.ronda.core.contract

import kotlin.random.Random

/** UUID v4 generado desde un [Random] inyectable, para que los escenarios sean reproducibles con semilla. */
fun Random.nextUuidV4(): String {
    val bytes = nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}
