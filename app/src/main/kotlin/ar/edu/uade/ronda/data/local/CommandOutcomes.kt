package ar.edu.uade.ronda.data.local

import ar.edu.uade.ronda.core.model.CommandStatus
import ar.edu.uade.ronda.core.repository.TrackedCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Convierte la lista de comandos seguidos en un flujo de transiciones: emite una vez por cada
 * cambio de estado de cada comando (PENDIENTE, CONFIRMADA, VERIFICADA...), que es lo que se guarda.
 */
fun Flow<List<TrackedCommand>>.toOutcomes(): Flow<CommandOutcome> {
    val upstream = this
    return flow {
        val seen = HashMap<String, CommandStatus>()
        upstream.collect { list ->
            list.forEach { t ->
                if (seen.put(t.id, t.status) != t.status) {
                    emit(CommandOutcome(t.command, t.status, t.updatedAt, t.reasonText))
                }
            }
        }
    }
}
