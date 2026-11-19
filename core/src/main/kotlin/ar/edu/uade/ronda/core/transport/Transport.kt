package ar.edu.uade.ronda.core.transport

import kotlinx.coroutines.flow.Flow

/**
 * Canal de texto entre la app y el robot. Hoy lo implementa el simulador; un gateway real
 * (WebSocket/HTTP) implementaría esta misma interfaz sin tocar repositorio ni ViewModels.
 */
interface Transport {
    /** Mensajes JSON del robot (telemetría, respuestas, eventos). */
    val incoming: Flow<String>

    /** Envía un mensaje JSON al robot. Si el enlace está caído se pierde sin avisar. */
    suspend fun send(text: String)
}
