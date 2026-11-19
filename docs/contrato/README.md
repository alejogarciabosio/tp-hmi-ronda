# Contrato de mensajes JSON

> Contrato propuesto por el grupo para un gateway propio entre la app y el robot. **No es el protocolo oficial de Unitree.** El SDK real del Go2 usa DDS (unitree_sdk2 / ROS 2); en una implementación real, el gateway traduciría estos mensajes a llamadas del SDK.

En la app, el simulador de `:core` produce y consume estos mismos mensajes a través de `ContractCodec`; los tests de parseo usan los archivos de esta carpeta.

## Convenciones

- `schemaVersion` (entero, hoy 1) y `type` en todos los mensajes: `TELEMETRY`, `COMMAND`, `COMMAND_ACK`, `EVENT`.
- `messageId` único por mensaje, en formato UUID v4 (ver decisión D7); `correlationId` en las respuestas, igual al `messageId` del comando. El decodificador los trata como texto opaco.
- Ids de dominio (`missionId`, `alertId`, `incident.id`, `routeId`, `safePointId`) legibles a propósito (`a-0007`, `i-0003`, `m-20251118-01`): no identifican mensajes sino objetos que el operador ve en pantalla y nombra al reconocer una alerta.
- `robotId` en todos los mensajes.
- Tiempos en epoch ms (`timestamp`, `issuedAt`, `receivedAt`, `raisedAt`). Los tres primeros, a nivel mensaje, llevan una copia legible en ISO 8601 (`timestampIso`, `issuedAtIso`, `receivedAtIso`), solo informativa: el código usa el epoch. `raisedAt`, dentro de cada alerta, no lleva copia ISO.
- Enums en MAYÚSCULAS; las unidades van en el nombre del campo (`speedMps`, `techRoomTempC`, `latencyMs`).
- `ttlMs` en los comandos: si llegan vencidos, el robot los descarta.
- `connection` no viaja en el mensaje: la deriva el repositorio por la antigüedad del último mensaje (más de 1,5 s degradado, más de 3 s sin datos, más de 10 s desconectado).

## Archivos

| Archivo | Contenido |
|---|---|
| `telemetria.json` | Telemetría a 2 Hz: batería, estado operativo, misión por puntos de control, pose, movimiento, alertas y los tres servicios (puntos verificados, temperatura de sala técnica, salidas de emergencia). |
| `comando-iniciar-mision.json` | `START_MISSION` con `routeId` (`RONDA_COMPLETA`, `SOLO_SALA_TECNICA`, `SALIDAS_EMERGENCIA`). |
| `comando-pausar.json` | `PAUSE_MISSION` con `missionId`. |
| `comando-reanudar.json` | `RESUME_MISSION` con `missionId`. |
| `comando-cancelar.json` | `CANCEL_MISSION` con `missionId`. |
| `comando-regresar-punto-seguro.json` | `RETURN_TO_SAFE_POINT` con `safePointId` (`BASE` o `DESCANSO_ESCALERA_E`). |
| `comando-detencion-operativa.json` | `OPERATIONAL_STOP`, sin parámetros. Es una orden de software: no reemplaza la parada de emergencia física. En la UI se presenta siempre como "Detención operativa (software)", nunca como parada de emergencia. |
| `comando-reconocer-alerta.json` | `ACKNOWLEDGE_ALERT` con `alertId`. |
| `respuesta-confirmada.json` | `COMMAND_ACK` con `status: CONFIRMED`. Confirmar es aceptar la orden, no haberla ejecutado: la ejecución se verifica en la telemetría. |
| `respuesta-rechazada.json` | `COMMAND_ACK` con `status: REJECTED`, `reasonCode` y `reasonText` (motivo legible para mostrar tal cual). |
| `evento-alerta.json` | `EVENT` con `event: ALERT_RAISED` y el objeto `alert`. |
| `evento-decision-requerida.json` | `EVENT` con `event: DECISION_REQUIRED` y un `incident` de tipo `OBSTACLE` con las opciones que ofrece el robot. |
| `evento-persona-zona-restringida.json` | `EVENT` con `DECISION_REQUIRED` y un `incident` de tipo `PERSON_IN_RESTRICTED_ZONE` (escenario 5). No lleva imágenes: solo el hecho, el punto de control y las opciones. |

## Estados de comando

El gateway solo responde `CONFIRMED` o `REJECTED`. Los estados `PENDIENTE` y `SIN_RESPUESTA` (timeout de 4 s) los calcula la app, y la ejecución verificada surge de la telemetría.

## Códigos de rechazo (`reasonCode`)

`INVALID_STATE`, `LOW_BATTERY`, `NOT_CONNECTED`, `COMMAND_PENDING`, `UNACKNOWLEDGED_ALERT`, `EXPIRED`, `UNKNOWN_TARGET`.

## Decisiones ante un incidente

Las `options` de `DECISION_REQUIRED` son las que la UI ofrece al operador. La respuesta viaja como uno de los siete comandos: regresar usa `RETURN_TO_SAFE_POINT`; esperar o saltar el punto de control usan `RESUME_MISSION` con el parámetro opcional `decision` (`WAIT_30S`, `SKIP_CHECKPOINT`); el resultado de la persona en zona restringida usa `ACKNOWLEDGE_ALERT` con `resolution`.

`checkpointIndex` del incidente es 1-based y usa la misma numeración que `mission.checkpointIndex`: la posición dentro de la ruta de la misión, del punto de control que el robot estaba alcanzando al detenerse (todavía no verificado). Los verificados hasta ahí se cuentan en `service.checkpointsVerified`. No es el número del PC del edificio: en la ruta `SOLO_SALA_TECNICA` la sala técnica (PC5) es el índice 3.

La opción `RETURN_TO_SAFE_POINT` lleva `safePointId` (`BASE` o `DESCANSO_ESCALERA_E`); la app lo copia al comando `RETURN_TO_SAFE_POINT` que envía. Las demás opciones no llevan `safePointId`.

Las opciones del incidente de persona en zona restringida (`AUTHORIZED_PERSON`, `SEND_GUARD`, `CONTINUE_ROUND`) son exactamente los valores de `resolution` en `ACKNOWLEDGE_ALERT` (con el `alertId` de la alerta `PERSON_DETECTED`): `AUTHORIZED_PERSON` registra a la persona autorizada y el robot sigue, `SEND_GUARD` avisa al vigilante y el robot espera sin cerrar la alerta, `CONTINUE_ROUND` sigue la ronda y marca el punto con observación.

## Valores de los enums

Todos en MAYÚSCULAS, salvo los estados operativos, el modo de movimiento y el estado de las salidas, que van en español como se muestran en el plan.

| Campo | Valores |
|---|---|
| `operationalState` | `EN_BASE`, `PREPARANDO`, `EN_RONDA`, `PAUSADA`, `REQUIERE_DECISION`, `DETENIDO`, `REGRESANDO`, `COMPLETADA`, `CANCELADA`, `FALLA` |
| `motion.mode` | `CAMINANDO`, `DETENIDO`, `ESCALERA`, `GIRANDO` |
| `alerts[].type` y `alert.type` | `OBSTACLE`, `LOW_BATTERY`, `PERSON_DETECTED`, `TECH_ROOM_TEMPERATURE`, `EXIT_BLOCKED`, `LINK_DEGRADED`, `ROBOT_FAULT` |
| `severity` (alerta y evento) | `INFO`, `WARNING`, `CRITICAL` |
| `incident.type` | `OBSTACLE`, `PERSON_IN_RESTRICTED_ZONE`, `BATTERY_RETURN` (el robot propone regresar por batería crítica; única opción: `RETURN_TO_SAFE_POINT`) |
| `service.techRoomTempTrend` | `RISING`, `STABLE`, `FALLING` |
| `service.emergencyExitsClear[].status` | `LIBRE`, `OBSTRUIDA`, `NO_VERIFICADA` |
| `params.routeId` | `RONDA_COMPLETA`, `SOLO_SALA_TECNICA`, `SALIDAS_EMERGENCIA` |
| `params.safePointId` | `BASE`, `DESCANSO_ESCALERA_E` |
| `params.decision` (opcional, `RESUME_MISSION`) | `WAIT_30S`, `SKIP_CHECKPOINT` |
| `params.resolution` (opcional, `ACKNOWLEDGE_ALERT`) | `AUTHORIZED_PERSON`, `SEND_GUARD`, `CONTINUE_ROUND` |
| `event` | `ALERT_RAISED`, `DECISION_REQUIRED` |
| `status` (respuesta) | `CONFIRMED`, `REJECTED` |

Los umbrales de temperatura de la sala técnica (advertencia sobre 27 °C, crítico sobre 32 °C) y el aviso de batería baja (25 %) y crítica (15 %) los aplica el robot al generar alertas.
