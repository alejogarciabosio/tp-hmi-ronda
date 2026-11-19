# Decisiones y limitaciones

Registro corto de decisiones de diseño (formato ADR) y limitaciones conocidas.

## D1. `:core` es Kotlin/JVM puro

- **Contexto.** El dominio, el contrato y el simulador no necesitan Android.
- **Decisión.** Módulo JVM sin dependencias de Android.
- **Consecuencias.** Se compila y testea con Gradle sin SDK; los cinco escenarios se prueban de punta a punta con tiempo virtual; el dominio queda separado de la UI.

## D2. El simulador habla JSON

- **Contexto.** Un simulador que entrega objetos Kotlin directamente haría que el contrato documentado quedara desconectado del código.
- **Decisión.** `RobotSimulator` emite mensajes serializados según `docs/contrato/` por un `FakeTransport`; el repositorio los parsea con `ContractCodec`.
- **Consecuencias.** El contrato se prueba porque se usa; cambiar a un gateway real implica escribir otro `Transport`. Costo: serializar y parsear en cada tick, irrelevante a 2 Hz. El contrato es propio, no el protocolo de Unitree.

## D3. Sin Hilt: `AppContainer`

- **Contexto.** El grafo de dependencias es chico (un repositorio de robot, uno de historial, la base).
- **Decisión.** Inyección manual con un `AppContainer` creado en `Application` y `ViewModelProvider.Factory` por pantalla.
- **Consecuencias.** Menos magia y tiempos de compilación menores, y las dependencias se leen en un solo archivo. A cambio, el cableado es manual si el grafo crece.

## D4. Room para el historial

- **Contexto.** El historial de misiones y eventos debe sobrevivir al cierre de la app y funcionar sin servidor.
- **Decisión.** Room con `MissionRecord` y `EventRecord`, detrás de `HistoryRepository`.
- **Consecuencias.** Persistencia local estándar de Android. La entidad queda en `:app`; `:core` no la conoce.

## D5. Progreso por puntos de control, no por porcentaje

- **Contexto.** El robot no puede conocer un porcentaje real de avance de una ronda (obstáculos, esperas, desvíos).
- **Decisión.** Se muestra "PC 4 de 8" y los puntos verificados con su hora; nunca un porcentaje de misión.
- **Consecuencias.** No se promete información que el robot no tiene. El único porcentaje en pantalla es el de batería, que sí es medido.

## D6. Mantener presionado para los comandos críticos

- **Contexto.** Cancelar, regresar y la detención operativa tienen consecuencia física; un diálogo de confirmación repetido se acepta por reflejo.
- **Decisión.** Estos comandos usan mantener presionado 1 s. Iniciar misión muestra un resumen previo de condiciones.
- **Consecuencias.** Evita toques accidentales sin sumar un diálogo modal; la detención operativa sigue accesible en todo momento. Es una orden de software y no reemplaza la parada de emergencia física.

## D7. `messageId` y `correlationId` como UUID v4

- **Contexto.** Una versión anterior usaba ids cortos con prefijo por emisor (`c-0001`, `r-0002`). Eran fáciles de leer, pero solo son únicos dentro de una sesión del simulador.
- **Decisión.** `messageId` y `correlationId` son UUID v4. Los genera quien emite el mensaje (repositorio para comandos, simulador para telemetría, respuestas y eventos) a partir de un `Random` inyectable: con la misma semilla la secuencia se repite, así los escenarios y las transcripciones siguen siendo reproducibles. Es un `Random` aparte del que decide la latencia, para que cambiar ids no altere los tiempos.
- **Qué sigue legible.** `missionId`, `routeId`, `safePointId`, `alertId` e `incident.id` mantienen ids cortos (`m-20251118-01`, `a-0007`, `i-0003`). No identifican mensajes sino objetos de dominio: el operador los ve en pantalla y en las evidencias, y los referencia al reconocer una alerta.
- **Validación.** `ContractCodec` los trata como string opaco y no valida el formato: el receptor solo necesita igualdad para correlacionar, y ser estricto rompería ante un gateway que use otro formato único. El formato se verifica en tests sobre los ejemplos y el generador.
- **Consecuencias.** Unicidad global entre emisores y reinicios, sin coordinar contadores. Las evidencias son menos cómodas de leer; se compensa con `type`, `command` y el orden temporal.

## D8. Coeficientes de batería y rediseño del escenario 2

- **Contexto.** El modelo anterior (0,08 %/m, 0,003 %/s detenido, 3,5 % por punto de control, reserva 10 %) daba ~25 min de autonomía caminando contra las 2-4 h declaradas por Unitree. `docs/supuestos-simulacion.md` (§2) recalcula los coeficientes a partir de los 432 Wh y esa autonomía.
- **Decisión.** Se aplican los valores de ese documento: 0,010 %/m, 0,007 %/s detenido, 1,0 % por punto de control, reserva 15 % y carga en base de 0,015 %/s. El mínimo para la ronda completa sigue en 40 % como regla operativa del grupo (§2.6): `START_MARGIN_PERCENT` pasa de 2 a 17 puntos para que `percentToStart` dé 8 x 1,0 + 15 + 17 = 40. Las rutas cortas quedan en 35 % (sala técnica) y 34 % (salidas).
- **Escenario 2.** Con estos coeficientes la ronda completa gasta ~5 a 7 % de la batería, así que ya no se llega al umbral con 45 % y consumo doble. Se evaluó que el robot ya estuviera en ronda con poca batería, pero la política de arranque lo impide: sale con el mínimo operativo. Se eligió partir del mínimo (40 %) con una celda degradada inyectada por `drainFactor = 6`: es una falla de batería, un caso real (envejecimiento, frío, celda desbalanceada) y es exactamente el motivo por el que el 40 % deja margen. La alerta a 25 % salta a mitad de ronda (PC 4), el robot propone regresar al 15 % (PC 7) y el operador confirma.
- **Consecuencias.** El robot estima el regreso con el consumo nominal; con la celda degradada llega a la base con ~2 %, por debajo de la reserva de 15 %. Se deja así porque ilustra el riesgo que la reserva cubre. En las demás ejecuciones el consumo es el realista y la batería casi no se mueve en una demo corta; el factor de aceleración solo se aplica en este escenario y se declara en la presentación.

## D9. Foto adjunta al incidente

- **Contexto.** Ante un obstáculo, una persona en zona restringida o una falla, el operador decide mejor si ve la escena. El robot tiene cámara frontal.
- **Decisión.** `incident` y `alert` suman un `snapshot` opcional (`id`, `mediaType`, `capturedAt`, `data` en base64 o `null`). Es una sola foto por incidente, no video: pesa decenas de KB (~640x360, JPEG medio, menos de 60 KB) y tolera el enlace degradado, donde un video sostenido no llega. El campo es opcional para no romper mensajes anteriores.
- **Simulador.** Manda `data = null` y ids fijos (`sim-obstaculo`, `sim-persona`, `sim-falla`); la app resuelve la imagen por `id`. `BATTERY_RETURN` no lleva foto.
- **Consecuencias.** En un robot real el JPEG saldría del servicio de video de la cámara frontal del SDK, vía el gateway. Una foto es un instante: no muestra cómo evoluciona la situación ni reemplaza la inspección en sitio.

## Limitaciones

### L1. Escalones mayores a 16 cm (riesgo R7)

El Go2 declara un escalón máximo de unos 16 cm. Esto no se puede simular: depende del edificio real. Antes de habilitar la ruta hay que relevar la contrahuella y el material de las dos escaleras; si alguna supera el límite, la ronda se limita a un piso. La app solo muestra el modo de movimiento ESCALERA. Ver `docs/riesgos.md`, R7.

### L2. Contrato JSON propio

El contrato de `docs/contrato/` es una propuesta del grupo y no el protocolo oficial de Unitree. Un gateway real traduciría estos mensajes a llamadas del SDK.
