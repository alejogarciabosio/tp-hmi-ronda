# Figma — RONDA

Archivo: https://www.figma.com/design/sHIJRf9HU7ci8PdgY7hmIN
fileKey: `sHIJRf9HU7ci8PdgY7hmIN`
Plan de la cuenta: **Starter** → estructura de 3 páginas con secciones (`docs/plan/FIGMA_SPEC.md` §1-B) y 1 solo modo por colección de variables.

## Estado (2026-11-19)

| Página | Sección | Estado |
|---|---|---|
| `00 Portada y mapa` (id `0:1`) | Portada · Mapa de navegación · Recorrido | **Pendiente.** La página está vacía (se borró el frame de prueba). |
| `01 Sistema` (id `1:4`) | Fundamentos (`5:2`) | **Hecho.** Hoja de colores, tipos, espaciado, radios, símbolos, grilla + primitivos. |
| | Componentes (`5:3`) | **Hecho.** 14 component sets con variantes, todo ligado a variables. |
| `02 Pantallas y flujos` (id `1:5`) | Pantallas · Flujo normal · Flujo con falla | **Pendiente.** |

Bloqueo: después de ~20 llamadas de escritura (`use_figma`) el MCP de Figma devolvió *"You've reached the Figma MCP tool call limit on the Starter plan"*. En Starter el cupo cuenta también las escrituras, no solo `get_screenshot`/`get_metadata`. Reintento único el 2026-11-19 03:38 UTC (listar páginas con `use_figma`): mismo error de límite. Hay que esperar a que se renueve el cupo o pasar el archivo a un plan Education/Professional para seguir (ver "Cómo retomar").

Detalle pendiente chico en Fundamentos: las 4 columnas de la muestra de grilla (`Grilla 412×917` dentro de `11:262`) quedaron en ámbar opaco; hay que bajarles la opacidad de capa a 14 %. El export `01-sistema-fundamentos.png` las muestra así.

## Tokens (copiar al tema Compose)

Colección `Color`, modo `Noche` (tema principal). Code syntax ANDROID: `RondaColors.<nombre>`.

| Variable Figma | Hex | Compose | Uso |
|---|---|---|---|
| `fondo` | `#14130F` | `ink950` | fondo de pantalla (grafito cálido) |
| `superficie` | `#1C1B17` | `ink900` | franjas, hojas inferiores, fila "¿Tenés que actuar? No" |
| `superficie-alta` | `#25241F` | `ink850` | renglón actual, alerta sin reconocer, banner de datos viejos |
| `regla` | `#3A382F` | `ink700` | reglas de 1 px, tabiques del plano |
| `texto/primario` | `#ECE6D8` | `bone100` | texto y datos (hueso) |
| `texto/secundario` | `#A39D8C` | `bone400` | rótulos, unidades, muros del plano |
| `texto/sobre-senal` | `#14130F` | `onSignal` | texto sobre ámbar |
| `senal/ambar` | `#F2A900` | `signalAmber` | único color de acción/atención |
| `estado/ok` | `#5FA77A` | `stateOk` | + círculo lleno |
| `estado/advertencia` | `#F2A900` | `stateWarn` | + triángulo + franjas diagonales |
| `estado/error` | `#E5482D` | `stateError` | + rombo |
| `estado/sin-datos` | `#6E6A5E` | `stateStale` | + círculo punteado + "vigente" tachado |
| `velo` | `#0B0A08` | `scrim` | velo detrás de la hoja de decisión, opacidad 70 % |

Tema día (muestra; grupo `dia/` en la misma colección porque Starter no permite 2 modos): `dia/papel #F4F0E6`, `dia/superficie #EAE4D6`, `dia/regla #C4BBA6`, `dia/tinta #1C1B17`, `dia/tinta-secundaria #5E594C`, `dia/ok #2F7A4C`, `dia/advertencia #A87500`, `dia/error #C2361E`.

Colección `Medidas` (modo `Valor`):
- Espaciado `esp/0,2,4,8,12,16,24,32` → `RondaSpacing.s0…s32` (dp).
- Radios `radio/0 = 0`, `radio/2 = 2`, `radio/4 = 4` → `RondaShapes.r0/r2/r4`. Radio por defecto de todo: **2 dp**.
- Bordes y reglas: 1 dp. Íconos: trazo 1,5 dp, caja 24 dp. Objetivos táctiles ≥ 48 dp.

Estilos de texto (Google Fonts, OFL):

| Estilo | Fuente | Tamaño / interlínea | Tracking | Caja |
|---|---|---|---|---|
| `estado/titulo` | Archivo ExtraBold | 26 / 30 | +2 % | mayúsculas |
| `pantalla/titulo` | Archivo Bold | 20 / 24 | 0 | — |
| `rotulo/seccion` | Archivo Narrow SemiBold | 12 / 16 | +4 % | mayúsculas |
| `rotulo/chico` | Archivo Narrow Medium | 11 / 14 | +4 % | mayúsculas |
| `boton/etiqueta` | Archivo SemiBold | 14 / 18 | +4 % | mayúsculas |
| `dato/grande` | IBM Plex Mono Medium | 22 / 28 | 0 | — (números tabulares) |
| `dato/normal` | IBM Plex Mono Regular | 14 / 20 | 0 | — |
| `dato/chico` | IBM Plex Mono Regular | 12 / 16 | 0 | — |
| `texto/cuerpo` | IBM Plex Sans Regular | 14 / 20 | 0 | — |
| `texto/enfasis` | IBM Plex Sans SemiBold | 14 / 20 | 0 | — |
| `texto/nota` | IBM Plex Sans Regular | 12 / 16 | 0 | — |
| `portada/display` | Archivo Black | 96 / 92 | −1 % | mayúsculas |

Grilla de pantalla: frame 412 × 917 (Android compacto), 4 columnas, márgenes 16, medianil 8.

Redundancia de estado: OK = círculo lleno verde · advertencia = triángulo ámbar + franjas · error = rombo bermellón · sin datos = círculo punteado gris · deshabilitado = contorno tachado + motivo escrito. Franjas de señalización: diagonales a 45°, 8 px de trazo cada 16 px, ámbar sobre `fondo`.

## Nodos clave — página `01 Sistema`

Primitivos (sección Fundamentos `5:2`):
- `Patron/Franjas` `5:4` (480×120, recorta; se usa como instancia dentro de un marco que recorta).
- `Simbolo` (set) `5:23`: `estado=ok 5:7`, `advertencia 5:10`, `error 5:13`, `sin-datos 5:16`, `deshabilitado 5:19`.
- `Icono/*` 24 px: supervision `5:25`, misiones `5:29`, alertas `5:34`, historial `5:38`, telemetria `5:42`, control `5:45`, llave `5:49`, tilde `5:52`, atras `5:55`, reloj `5:58`, senal `5:62`, bateria `5:65`, persona `5:69`, salida `5:73`, termometro `5:76`.
- `RobotPose` `5:80` (chevron ámbar; rotar según `pose.headingDeg`).
- Hoja de documentación `Doc/Fundamentos` `11:262`.

Component sets (sección Componentes `5:3`):

| Set | ID | Variantes |
|---|---|---|
| `StateStrip` | `8:427` | estado = EN BASE / EN RONDA / PAUSADA / REQUIERE DECISIÓN / DETENIDO / REGRESANDO / FALLA × needsAction = no / sí |
| `FloorPlan` | `10:326` | floor = PB (`10:262`) / 1° (`10:292`); RobotPose anidado (`robot`) |
| `CheckpointRow` | `6:127` | state = pendiente / actual / verificado / con observación / salteado |
| `TelemetryReadout` | `7:139` | state = normal / warning / error / stale × withTrend = sí / no |
| `CommandButton` | `7:270` | kind = primary / secondary / destructive × state = enabled / disabled / pending / confirmed / rejected / noResponse |
| `HoldToConfirm` | `7:291` | progress = 0 / 50 / 100 |
| `CommandChip` | `6:77` | status = PENDIENTE / CONFIRMADA / VERIFICADA / RECHAZADA / SIN RESPUESTA |
| `StatusBadge` | `6:50` | state = ok / warning / error / stale / disabled × size = sm / lg |
| `SimTag` | `6:51` | (único) borde rayado + "SIMULACIÓN" |
| `AlertRow` | `7:352` | severity = info / warning / critical × acknowledged = sí / no |
| `StaleDataBanner` | `7:380` | seconds = 4 (SIN DATOS) / 12 (DESCONECTADO) / 30 (+ política declarada) |
| `DecisionSheet` | `8:651` | opciones = 2 (propuesta de regreso por batería) / 3 (obstáculo en PC4) |
| `RobotCard` | `8:589` | availability = disponible / ocupado / no compatible |
| `NavBar` | `8:540` | tab = supervision / misiones / alertas / historial |

Capas de texto que se sobrescriben en instancias (por nombre): `etiqueta`, `motivo`, `estado`, `detalle`, `respuesta`, `conexion`, `valor`, `edad`, `tendencia`, `titulo`, `hora`, `codigo`, `nombre`.

Decisiones visibles en los componentes (para que Compose las copie):
- Botón destructivo = contorno bermellón + "mantener 1 s"; la Detención operativa (software) siempre va con la nota "No reemplaza la parada física del robot."
- Conexión en la franja: "CONECTADO · 0,4 s" siempre con antigüedad; con datos viejos el estado pasa a SIN DATOS / DESCONECTADO (nunca "Conectado" con telemetría vieja).
- Progreso por puntos de control ("PC 3 de 8 · Escalera E"), nunca porcentaje.

## Exports (`design/exports/`)

- `01-sistema-fundamentos.png` — sección Fundamentos (la muestra de grilla todavía sin opacidad).
- `01-sistema-componentes.png` — sección Componentes completa.
- `01-componente-statestrip.png`, `01-componente-floorplan.png`, `01-componente-decisionsheet.png`, `01-componente-commandbutton.png` — detalle.

## Lo que falta (en orden)

1. **`00 Portada y mapa`**: Portada (RONDA · Cuaderno de ronda, integrantes, Desarrollo de Aplicaciones I · UADE, caso "Ronda nocturna con Unitree Go2 EDU", fecha, índice); Mapa de navegación; Recorrido (instancias de `FloorPlan` PB y 1° ampliadas al 200 % con leyenda: BASE garita, PC1–PC8, salidas, escaleras E/O, punto seguro alternativo "Descanso escalera E").
2. **`02 Pantallas y flujos` → sección Pantallas** (frames 412×917, fondo `fondo`, `StateStrip` arriba, `NavBar` abajo, todo con instancias):
   - P01 Selección de robot (3 × `RobotCard`).
   - P02 Supervisión — en ronda PC 3/8 (StateStrip EN RONDA/no, FloorPlan PB, 4 TelemetryReadout: batería "62 % · alcanza para volver: sí", conexión, temp. sala técnica, salidas libres 2/2; CheckpointRow ×4; botones Pausar / Regresar a base).
   - P02b Supervisión — batería baja (EN RONDA/sí, readout batería warning 24 %, AlertRow warning).
   - P02c Supervisión — incidente (REQUIERE DECISIÓN/sí, AlertRow critical "Persona en zona restringida · PC5").
   - P02d Supervisión — sin datos (StaleDataBanner 12, readouts stale, comandos disabled con motivo salvo Detención operativa).
   - P02e Supervisión — en base · P02f Supervisión — PC 8/8 · P02g Ronda completada.
   - P03 Telemetría (todas las filas TelemetryReadout).
   - P04 Misiones (3 rutas + preflight OK) · P04b Preflight con check fallido ("Batería insuficiente para la ronda completa (necesita 40 %)", Iniciar ronda disabled).
   - P05 Control (CommandButton disabled con motivo, uno pending, uno rejected, uno noResponse + registro con CommandChip + aviso permanente de detención por software).
   - P06 Alertas · P07 Historial · P08 Desconexión (StaleDataBanner 30) · P09 Panel de simulación (SimTag, presets 1–5, toggles) · P10 Decisión requerida (P02 + velo 70 % + DecisionSheet 3) · P11 Reconexión y resincronización.
3. **Flujo normal** y **Flujo con falla**: clones de las pantallas en fila, numerados, con las conexiones de abajo.

## Prototipo — conexiones para hacer a mano (ON_CLICK → NAVIGATE)

Flujo normal (inicio de flujo: "Flujo normal", en N1):
1. N1 Selección · `RobotCard/disponible` → botón "Tomar control" → N2 Supervisión en base
2. N2 · NavBar tab "Misiones" → N3 Misiones
3. N3 · fila "Ronda completa" → N4 Preflight
4. N4 · "Iniciar ronda" → N5 Supervisión en ronda PC 3/8
5. N5 · (After delay 3000 ms) → N6 Supervisión PC 8/8
6. N6 · (After delay 3000 ms) → N7 Ronda completada
7. N7 · "Ver en historial" → N8 Historial

Flujo con falla (inicio de flujo: "Flujo con falla", en F1):
1. F1 Supervisión en ronda · AlertRow critical / franja "Sí: decidí cómo seguir en PC4" → F2 Alerta obstáculo (Alertas)
2. F2 · "Reconocer" → F3 Decisión requerida
3. F3 · opción 3 "Regresar a base" (HoldToConfirm) → F4 Supervisión REGRESANDO
4. F4 · (After delay 2000 ms) → F5 Desconexión (datos antiguos, comando Regresar SIN RESPUESTA)
5. F5 · "Reintentar conexión" → F6 Reconexión y resincronización
6. F6 · "Confirmar comandos pendientes" → F7 Supervisión EN BASE
7. F7 · NavBar "Historial" → F8 Historial con el incidente

Navegación común (en todas las pantallas con NavBar): Supervisión / Misiones / Alertas / Historial → pantalla correspondiente; desde Supervisión, fila de telemetría → Telemetría y barra de comandos → Control; "llave" (long-press) → Panel de simulación; flecha atrás → pantalla anterior (Back).

## Cómo retomar

Con cupo disponible: `create_session` con el fileKey, cargar `figma-use` + `figma-generate-design`, y construir en este orden: Pantallas (una llamada por 2–3 pantallas, con instancias de los sets de arriba por ID) → flujos (clonar frames) → prototipo con `setReactionsAsync` → página 00. Verificar con `node.screenshot()` dentro de `use_figma`, nunca `get_screenshot`.
