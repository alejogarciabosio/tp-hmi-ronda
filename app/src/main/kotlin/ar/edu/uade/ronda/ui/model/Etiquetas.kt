package ar.edu.uade.ronda.ui.model

import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.data.local.EventKind

/** Único lugar donde un tipo del dominio se convierte en texto para el operador: la UI nunca muestra `.name`. */
object Etiquetas {
    fun alerta(type: AlertType) = when (type) {
        AlertType.OBSTACLE -> "Obstáculo"
        AlertType.LOW_BATTERY -> "Batería baja"
        AlertType.PERSON_DETECTED -> "Persona en zona restringida"
        AlertType.TECH_ROOM_TEMPERATURE -> "Temperatura de sala técnica"
        AlertType.EXIT_BLOCKED -> "Salida de emergencia obstruida"
        AlertType.LINK_DEGRADED -> "Enlace degradado"
        AlertType.ROBOT_FAULT -> "Falla del robot"
    }

    fun incidente(type: IncidentType) = when (type) {
        IncidentType.OBSTACLE -> "Obstáculo"
        IncidentType.PERSON_IN_RESTRICTED_ZONE -> "Persona en zona restringida"
        IncidentType.BATTERY_RETURN -> "Batería crítica: regresar a la base"
    }

    fun comando(type: CommandType) = when (type) {
        CommandType.START_MISSION -> "Iniciar ronda"
        CommandType.PAUSE_MISSION -> "Pausar"
        CommandType.RESUME_MISSION -> "Reanudar"
        CommandType.CANCEL_MISSION -> "Cancelar misión"
        CommandType.RETURN_TO_SAFE_POINT -> "Regresar a base"
        CommandType.OPERATIONAL_STOP -> "Detención operativa"
        CommandType.ACKNOWLEDGE_ALERT -> "Reconocer alerta"
    }

    /** Etiqueta de una fila del historial, que guarda el tipo como texto según su `kind`. */
    fun registro(kind: String, type: String): String = when (kind) {
        EventKind.ALERTA.name -> AlertType.entries.firstOrNull { it.name == type }?.let(::alerta)
        EventKind.INCIDENTE.name -> IncidentType.entries.firstOrNull { it.name == type }?.let(::incidente)
        EventKind.COMANDO.name -> CommandType.entries.firstOrNull { it.name == type }?.let(::comando)
        else -> null
    } ?: "Evento"
}
