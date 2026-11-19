# Contrato de mensajes JSON

> Contrato propuesto por el grupo para un gateway propio entre la app y el robot. **No es el protocolo oficial de Unitree.** El SDK real del Go2 usa DDS (unitree_sdk2 / ROS 2); en una implementación real, el gateway traduciría estos mensajes a llamadas del SDK.

En la app, el simulador de `:core` produce y consume estos mismos mensajes a través de `ContractCodec`; los tests de parseo usan los archivos de esta carpeta.

## Convenciones

- `schemaVersion` (entero, hoy 1) y `type` en todos los mensajes: `TELEMETRY`, `COMMAND`, `COMMAND_ACK`, `EVENT`.
- `messageId` único por mensaje; `correlationId` en las respuestas, igual al `messageId` del comando.
- `robotId` en todos los mensajes.
- Tiempos en epoch ms (`timestamp`, `issuedAt`, `receivedAt`, `raisedAt`) con una copia legible en ISO 8601 (`*Iso`), solo informativa: el código usa el epoch.
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
| `comando-detencion-operativa.json` | `OPERATIONAL_STOP`, sin parámetros. Es una orden de software: no reemplaza la parada de emergencia física. |
| `comando-reconocer-alerta.json` | `ACKNOWLEDGE_ALERT` con `alertId`. |
| `respuesta-confirmada.json` | `COMMAND_ACK` con `status: CONFIRMED`. Confirmar es aceptar la orden, no haberla ejecutado: la ejecución se verifica en la telemetría. |
| `respuesta-rechazada.json` | `COMMAND_ACK` con `status: REJECTED`, `reasonCode` y `reasonText` (motivo legible para mostrar tal cual). |
| `evento-alerta.json` | `EVENT` con `event: ALERT_RAISED` y el objeto `alert`. |
| `evento-decision-requerida.json` | `EVENT` con `event: DECISION_REQUIRED` y un `incident` con las opciones que ofrece el robot. |

## Estados de comando

El gateway solo responde `CONFIRMED` o `REJECTED`. Los estados `PENDIENTE` y `SIN_RESPUESTA` (timeout de 4 s) los calcula la app, y la ejecución verificada surge de la telemetría.

## Códigos de rechazo (`reasonCode`)

`INVALID_STATE`, `LOW_BATTERY`, `NOT_CONNECTED`, `COMMAND_PENDING`, `UNACKNOWLEDGED_ALERT`, `EXPIRED`, `UNKNOWN_TARGET`.

## Decisiones ante un incidente

Las `options` de `DECISION_REQUIRED` son las que la UI ofrece al operador. La respuesta viaja como uno de los siete comandos: regresar usa `RETURN_TO_SAFE_POINT`; esperar o saltar el punto de control usan `RESUME_MISSION` con el parámetro opcional `decision` (`WAIT_30S`, `SKIP_CHECKPOINT`); el resultado de la persona en zona restringida usa `ACKNOWLEDGE_ALERT` con `resolution`.
