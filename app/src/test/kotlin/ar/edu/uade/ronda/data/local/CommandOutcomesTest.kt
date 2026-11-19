package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.repository.TrackedCommand
import ar.edu.uade.ronda.core.model.Command
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandOutcomesTest {
    private fun t(id: String, status: CommandStatus, at: Long) =
        TrackedCommand(Command(id, "RONDA-01", 0, "garita", CommandType.PAUSE_MISSION), status, 0, at)

    @Test
    fun `emite una vez por cada cambio de estado y no repite los iguales`() = runTest {
        val lists = flowOf(
            listOf(t("a", CommandStatus.PENDING, 0)),
            listOf(t("a", CommandStatus.PENDING, 0), t("b", CommandStatus.PENDING, 5)),
            listOf(t("a", CommandStatus.CONFIRMED, 10), t("b", CommandStatus.PENDING, 5)),
            listOf(t("a", CommandStatus.VERIFIED, 20), t("b", CommandStatus.NO_RESPONSE, 30)),
        )
        val out = lists.toOutcomes().toList().map { it.command.messageId to it.status }
        assertEquals(
            listOf(
                "a" to CommandStatus.PENDING, "b" to CommandStatus.PENDING, "a" to CommandStatus.CONFIRMED,
                "a" to CommandStatus.VERIFIED, "b" to CommandStatus.NO_RESPONSE,
            ),
            out,
        )
    }
}
