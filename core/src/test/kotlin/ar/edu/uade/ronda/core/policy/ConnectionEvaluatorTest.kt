package ar.edu.uade.ronda.core.policy

import ar.edu.uade.ronda.core.model.ConnectionState
import ar.edu.uade.ronda.core.model.ConnectionState.CONNECTED
import ar.edu.uade.ronda.core.model.ConnectionState.DEGRADED
import ar.edu.uade.ronda.core.model.ConnectionState.DISCONNECTED
import ar.edu.uade.ronda.core.model.ConnectionState.NO_DATA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConnectionEvaluatorTest {
    private val evaluator = ConnectionEvaluator()
    private val now = 1_000_000L

    @ParameterizedTest(name = "edad {0} ms, latencia {1} ms -> {2}")
    @CsvSource(
        "0, 100, CONNECTED",
        "500, 120, CONNECTED",
        "1500, 100, CONNECTED",
        "1501, 100, DEGRADED",
        "3000, 100, DEGRADED",
        "3001, 100, NO_DATA",
        "10000, 100, NO_DATA",
        "10001, 100, DISCONNECTED",
        "60000, 20, DISCONNECTED",
        "200, 1000, CONNECTED",
        "200, 1001, DEGRADED",
        "4000, 5000, NO_DATA",
    )
    fun `estado segun antiguedad y latencia`(ageMs: Long, latencyMs: Int, expected: ConnectionState) {
        assertEquals(expected, evaluator.evaluate(now - ageMs, latencyMs, now))
    }

    @Test
    fun `sin mensajes previos esta desconectado`() {
        assertEquals(DISCONNECTED, evaluator.evaluate(null, null, now))
    }

    @Test
    fun `sin latencia conocida decide solo la antiguedad`() {
        assertEquals(CONNECTED, evaluator.evaluate(now - 100, null, now))
    }

    @Test
    fun `un mensaje del futuro por desfase de reloj no rompe nada`() {
        assertEquals(CONNECTED, evaluator.evaluate(now + 5_000, 50, now))
    }

    @Test
    fun `datos viejos nunca cuentan como conectado aunque la ultima latencia fuera buena`() {
        val estados = (0L..20_000L step 250).map { evaluator.evaluate(now - it, 10, now) }
        // el estado solo empeora a medida que envejece el dato
        assertEquals(estados.sortedBy { it.ordinal }, estados)
        assertTrue(estados.last().isStale)
        assertFalse(evaluator.evaluate(now - 3_500, 10, now) == CONNECTED)
    }
}
