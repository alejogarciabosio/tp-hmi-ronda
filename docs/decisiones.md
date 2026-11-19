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

- **Contexto.** Cancelar, regresar a la base y la detención operativa tienen consecuencia física; un diálogo de confirmación repetido se acepta por reflejo.
- **Decisión.** Estos tres comandos usan mantener presionado 1 s (`HoldToConfirm`, el mismo componente en Control, Supervisión y Desconexión). "Regresar a la base" se confirma igual que "Cancelar"; "Regresar a punto seguro" y el resto de las órdenes son de un toque. Iniciar misión muestra un resumen previo de condiciones.
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

## D10. Panel de simulación

- **Contexto.** El enunciado pide forzar desconexiones y rechazos (R19) y demostrar cinco escenarios (R21). El Figma propone cinco botones distintos (ronda normal, obstáculo, enlace cortado, enlace restaurado, pausa rechazada).
- **Decisión.** El panel carga los cinco escenarios del plan (`SimScenario`: ronda normal, batería baja, desconexión, obstáculo y persona en zona restringida), que son los que exige R21. Cargar un escenario reinicia el robot en la base y muestra qué hacer a continuación; el operador inicia la ronda desde Misiones como en una ronda real. Las fallas (cortar enlace, cortar 15 s, rechazar la próxima orden, demorar respuestas, inyectar obstáculo, persona o falla, batería ±10 %) se pueden combinar con cualquier escenario.
- **Detalles.** La demora es de 5 s y no de 3 s como en el Figma: las órdenes vencen a los 4 s, y con 3 s la respuesta llegaba a tiempo y nunca se veía SIN RESPUESTA. El rechazo armado no se puede desarmar desde el simulador: se consume con la próxima orden, y el interruptor lo refleja. El ViewModel del panel recibe la interfaz `SimulationControl` (`core.control`), nunca la implementación de `core.sim`.
- **Consecuencias.** El panel es una herramienta de demo, marcada con franjas y el rótulo SIMULACIÓN. En una instalación real no existe.

## D11. Control exclusivo con `ControlSession`; la detención operativa no depende del control

- **Contexto.** Dos operadores con órdenes contradictorias sobre el mismo robot es un riesgo (RG6). Al mismo tiempo, frenar al robot nunca debe quedar bloqueado por una regla de permisos.
- **Decisión.** `ui/session/ControlSession` guarda qué robot controla este operador (`StateFlow<String?>`, una instancia en `AppContainer`). Selección lo toma o lo suelta; Supervisión y Control lo leen. `ControlSession.gate` se aplica a la disponibilidad que calcula `CommandPolicy`: sin control propio, toda orden queda deshabilitada con el motivo "Tomá el control del robot en Selección". Quedan fuera la **detención operativa (software)**, que conserva su disponibilidad ("detener siempre gana"), y **Reconocer alerta**, que la pantalla de Alertas toma de la política sin pasar por el filtro.
- **Por qué fuera de `CommandPolicy`.** El control es estado de la sesión de la app, no del robot ni del contrato: la política del dominio sigue siendo una función del estado del robot y es la misma con o sin sesión.
- **Consecuencias.** Es una regla local de la app: no hay arbitraje entre dos celulares (el simulador tiene un solo cliente), así que el "RONDA-03 en uso por otra garita" de Selección es un dato de ejemplo. Un gateway real tendría que aplicar la exclusividad del lado del robot. La detención operativa sigue siendo una orden de software y no reemplaza la parada de emergencia física.

## Limitaciones

### L1. Escalones mayores a 16 cm (riesgo RG7)

El Go2 declara un escalón máximo de unos 16 cm. Esto no se puede simular: depende del edificio real. Antes de habilitar la ruta hay que relevar la contrahuella y el material de las dos escaleras; si alguna supera el límite, la ronda se limita a un piso. La app solo muestra el modo de movimiento ESCALERA. Ver `docs/riesgos.md`, RG7.

### L2. Contrato JSON propio

El contrato de `docs/contrato/` es una propuesta del grupo y no el protocolo oficial de Unitree. Un gateway real traduciría estos mensajes a llamadas del SDK.

### L3. Sin velocidad de simulación ×4

El plan preveía un selector ×1/×4 en el panel. No se implementó: el simulador avanza con el reloj real y acelerarlo exige un reloj propio en `core`. Una ronda completa dura unos 8 minutos y medio, así que la demo usa las inyecciones del panel (batería, obstáculo, persona, corte de enlace) para llegar a cada situación sin esperar la ronda entera.

### L4. Diferencias entre la app y el Figma

- **Batería mínima por ruta:** la app exige 40 % para la ronda completa, 35 % para la sala técnica y 34 % para las salidas de emergencia (`BatteryPolicy`, D8). El Figma muestra 20 % y 15 % para las rutas cortas, valores anteriores al recálculo de D8.
- **Posición de la base:** la app dibuja la base donde la ubica `BuildingLayout.base`, arriba a la izquierda de PB, para que el robot en base quede sobre el cuadro BASE. El Figma y `recorrido.svg` la ubican abajo a la izquierda, en la garita (ver `design/FIGMA.md`).
- **Panel de simulación:** botones de escenario y demora distintos (ver D10) y sin selector de velocidad (L3).
- **Color del dato viejo:** la app usa `#908B7C` (ver L5); el Figma conserva `#6E6A5E` en `estado/sin-datos`.

### L5. Contraste del gris de dato viejo

El gris que marca un dato no vigente o un control deshabilitado (`stateStale`, `#908B7C`) tiene un contraste de 5,46:1 sobre el fondo principal (`ink950`) y de 4,73:1 sobre la tarjeta elegida (`ink850`), por encima del 4,5:1 de WCAG para texto (`ContrasteTest`). Un valor anterior (`#6E6A5E`, 3,4:1) no pasaba y se aclaró. Sigue siendo más tenue que el texto secundario (6,9:1) para que el dato viejo no se lea como vigente, y nunca va solo: lo acompañan el símbolo de estado, el tachado y el motivo escrito.
