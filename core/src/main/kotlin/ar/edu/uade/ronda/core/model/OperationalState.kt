package ar.edu.uade.ronda.core.model

/** Estado operativo del robot (plan §3.1). Los nombres coinciden con el contrato JSON. */
enum class OperationalState {
    EN_BASE,
    PREPARANDO,
    EN_RONDA,
    PAUSADA,
    REQUIERE_DECISION,
    DETENIDO,
    REGRESANDO,
    COMPLETADA,
    CANCELADA,
    FALLA;

    /** El robot se está desplazando o está por hacerlo. */
    val inMotion: Boolean
        get() = this == PREPARANDO || this == EN_RONDA || this == REGRESANDO || this == REQUIERE_DECISION
}

/**
 * Estado del enlace. No viaja en el mensaje: lo deriva el repositorio por la antigüedad
 * del último mensaje (ver `ConnectionEvaluator`).
 */
enum class ConnectionState {
    CONNECTED,
    DEGRADED,
    NO_DATA,
    DISCONNECTED;

    /** Con SIN_DATOS o peor, lo último que se vio ya no es confiable. */
    val isStale: Boolean
        get() = this == NO_DATA || this == DISCONNECTED
}
