package ar.edu.uade.ronda.ui.model

/** Modelos de UI simples: los arman los ViewModels a partir del dominio. */

/** Nivel de estado; cada uno tiene color, forma y texto (nunca solo color). */
enum class StatusLevel { OK, WARNING, ERROR, STALE, DISABLED }

data class StatusBadgeUi(val level: StatusLevel, val label: String)

enum class Trend { RISING, STABLE, FALLING }

data class TelemetryReadoutUi(
    val label: String,
    val value: String,
    val unit: String = "",
    val level: StatusLevel = StatusLevel.OK,
    val trend: Trend? = null,
    /** Antigüedad del dato, p. ej. "hace 0,4 s". Siempre se muestra. */
    val age: String,
    val detail: String? = null,
)

enum class CommandKind { PRIMARY, SECONDARY, DESTRUCTIVE }

enum class CommandUiState { ENABLED, DISABLED, PENDING, CONFIRMED, REJECTED, NO_RESPONSE }

data class CommandButtonUi(
    val label: String,
    val kind: CommandKind = CommandKind.PRIMARY,
    val state: CommandUiState = CommandUiState.ENABLED,
    /** Motivo escrito cuando está deshabilitado, rechazado o sin respuesta. */
    val reason: String? = null,
)

enum class CommandChipStatus { PENDING, CONFIRMED, VERIFIED, REJECTED, NO_RESPONSE }

data class CommandChipUi(val status: CommandChipStatus)

enum class CheckpointState { PENDING, CURRENT, VERIFIED, WITH_ISSUE, SKIPPED }

data class CheckpointUi(
    val index: Int,
    val name: String,
    val state: CheckpointState,
    /** Hora de verificación, "02:14", si corresponde. */
    val time: String? = null,
    val floor: Int = 0,
    /** Posición en el plano, normalizada 0..1. */
    val x: Float = 0f,
    val y: Float = 0f,
)

data class RobotPoseUi(val floor: Int, val x: Float, val y: Float, val headingDeg: Float)

data class FloorPlanUi(
    val floor: Int,
    val floorLabel: String,
    val checkpoints: List<CheckpointUi>,
    val robot: RobotPoseUi?,
)

data class StateStripUi(
    val stateLabel: String,
    val level: StatusLevel,
    val needsAction: Boolean,
    /** "No" o "Sí: decidí cómo seguir en PC4". */
    val actionText: String,
    /** "CONECTADO · 0,4 s": siempre con antigüedad. */
    val connectionLabel: String,
    val connectionLevel: StatusLevel,
    /** "SIMULACIÓN ×4" cuando el tiempo está acelerado: nadie debe confundirlo con tiempo real. */
    val simulationBadge: String? = null,
)

data class StaleBannerUi(
    val secondsWithoutData: Int,
    /** Política declarada del robot sin enlace; se muestra desde 30 s. */
    val policyText: String? = null,
)
