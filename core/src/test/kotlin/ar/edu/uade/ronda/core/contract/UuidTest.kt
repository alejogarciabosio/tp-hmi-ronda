package ar.edu.uade.ronda.core.contract

import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidTest {
    @Test
    fun `genera UUID v4 validos`() {
        val random = Random(1)
        repeat(200) {
            val id = random.nextUuidV4()
            val parsed = UUID.fromString(id)
            assertEquals(4, parsed.version())
            assertEquals(2, parsed.variant())
            assertEquals(id, parsed.toString())
        }
    }

    @Test
    fun `misma semilla da la misma secuencia y distinta semilla otra`() {
        assertEquals(Random(5).nextUuidV4(), Random(5).nextUuidV4())
        assertNotEquals(Random(5).nextUuidV4(), Random(6).nextUuidV4())
    }

    @Test
    fun `los ejemplos del contrato usan UUID en messageId y correlationId`() {
        val dir = listOf("../docs/contrato", "docs/contrato").map(::File).first { it.isDirectory }
        val regex = Regex("\"(?:messageId|correlationId)\"\\s*:\\s*\"([^\"]+)\"")
        val ids = dir.listFiles { f -> f.extension == "json" }!!.flatMap { f ->
            regex.findAll(f.readText()).map { it.groupValues[1] }.toList()
        }
        assertTrue(ids.isNotEmpty())
        ids.forEach { assertEquals(4, UUID.fromString(it).version(), it) }
    }
}
