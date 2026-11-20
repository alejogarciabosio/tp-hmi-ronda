# Figma — RONDA

Archivo: https://www.figma.com/design/13dj5SYTN8of0MSQdIshDI
fileKey: `13dj5SYTN8of0MSQdIshDI` · plan **Education** (archivo organizado en ocho páginas: portada, fundamentos, componentes, pantallas, flujo normal, flujo con falla, mapa de navegación y recorrido).

El archivo anterior (`sHIJRf9HU7ci8PdgY7hmIN`, plan Starter) quedó **obsoleto**: no se usa ni se actualiza.

## Estado por página (2026-11-19)

| Página | ID | Contenido | Nodo principal |
|---|---|---|---|
| `00 Portada` | `0:1` | RONDA · Cuaderno de ronda, materia, integrantes, caso, fecha, índice con links a las páginas y aviso de que el contrato JSON es propuesta propia | frame `Portada` `29:112` |
| `01 Fundamentos` | `1:2` | Hoja de colores (Noche y Día), tipos, medidas, símbolos, íconos y grilla + primitivos | `Doc/Fundamentos` `4:2`, sección `Primitivos` |
| `02 Componentes` | `1:3` | 18 component sets + `SimTag` + `Foto/*` | sección `Componentes` `12:297` |
| `03 Pantallas` | `1:4` | 23 pantallas en 4 filas (Supervisión · Inicio y misiones · Control, alertas e historial · Conexión, decisión y simulación) | sección `Pantallas` `21:2868` |
| `04 Flujo normal` | `1:5` | N1–N8 conectados, inicio de flujo "Flujo normal" | sección `25:2` |
| `05 Flujo con falla` | `1:6` | F1–F8 + F3b, banda "la máquina propone, la persona decide", inicio "Flujo con falla" | sección `26:2` |
| `06 Mapa de navegación` | `1:7` | Todas las pantallas con flechas y condiciones | sección `29:2` |
| `07 Recorrido` | `1:8` | Plano de `docs/arquitectura/recorrido.svg` en tema noche con leyenda + FloorPlan PB y 1° al 200 % | sección `27:2` |

## Tokens (copiar al tema Compose)

Colección `Color` (`VariableCollectionId:2:2`), modos `Noche` (principal, `2:0`) y `Día` (`2:1`). Code syntax ANDROID: `RondaColors.<nombre>`.

| Variable Figma | Noche | Día | Compose | Uso |
|---|---|---|---|---|
| `fondo` | `#14130F` | `#F4F0E6` | `ink950` | fondo de pantalla (grafito cálido / papel) |
| `superficie` | `#1C1B17` | `#EAE4D6` | `ink900` | franjas, hojas inferiores, fila "¿Tenés que actuar? No" |
| `superficie-alta` | `#25241F` | `#DFD8C6`* | `ink850` | renglón actual, alerta sin reconocer, banner de datos viejos |
| `regla` | `#3A382F` | `#C4BBA6` | `ink700` | reglas de 1 px, tabiques del plano |
| `texto/primario` | `#ECE6D8` | `#1C1B17` | `bone100` | texto y datos |
| `texto/secundario` | `#A39D8C` | `#5E594C` | `bone400` | rótulos, unidades, muros del plano |
| `texto/sobre-senal` | `#14130F` | `#14130F` | `onSignal` | texto sobre ámbar |
| `senal/ambar` | `#F2A900` | `#A87500`* | `signalAmber` | único color de acción/atención |
| `estado/ok` | `#5FA77A` | `#2F7A4C` | `stateOk` | + círculo lleno |
| `estado/advertencia` | `#F2A900` | `#A87500` | `stateWarn` | + triángulo + franjas diagonales |
| `estado/error` | `#E5482D` | `#C2361E` | `stateError` | + rombo |
| `estado/sin-datos` | `#908B7C` | `#7C7767`* | `stateStale` (5,46:1 sobre el fondo, ver L4 en `docs/decisiones.md`) | + círculo punteado + valor tachado |
| `velo` | `#0B0A08` | `#1C1B17`* | `scrim` | velo detrás de la hoja de decisión, opacidad 70 % |

\* Valores de Día sin equivalente en la paleta original; son decisión de diseño. `senal/ambar` en Día baja a `#A87500` porque `#F2A900` no se lee sobre papel (contra `texto/sobre-senal` da 4,5:1).

Colección `Medidas` (`VariableCollectionId:2:16`, modo `Valor`):
- Espaciado `esp/0,2,4,8,12,16,24,32` → `RondaSpacing.s0…s32` (dp).
- Radios `radio/0 = 0`, `radio/2 = 2`, `radio/4 = 4` → `RondaShapes.r0/r2/r4`. Radio por defecto: **2 dp**; la foto usa 4.
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

Grilla de pantalla: frame 412 × 917 (Android compacto), 4 columnas de 89, márgenes 16, medianil 8 (muestra `4:342`, columnas al 14 %).

Redundancia de estado: OK = círculo lleno verde · advertencia = triángulo ámbar + franjas · error = rombo bermellón · sin datos = círculo punteado gris · deshabilitado = contorno tachado + motivo escrito. Franjas: diagonales a 45°, 8 px cada 16 px, ámbar sobre `fondo`.

## Primitivos (`01 Fundamentos` y `02 Componentes`)

- `Patron/Franjas` `3:35` (480×120, recorta; usar como instancia dentro de un marco).
- `Simbolo` (set) `3:52`: `ok 3:38`, `advertencia 3:41`, `error 3:44`, `sin-datos 3:47`, `deshabilitado 3:51`.
- `Icono/*` 24 px: supervision `3:59`, misiones `3:62`, alertas `3:66`, historial `3:71`, telemetria `3:74`, control `3:77`, llave `3:81`, tilde `3:84`, atras `3:87`, reloj `3:91`, senal `3:94`, bateria `3:97`, persona `3:101`, salida `3:104`, termometro `3:107`.
- `RobotPose` `3:55` (chevron ámbar; rumbo 0 = arriba).
- `Foto/obstaculo` `22:201`, `Foto/persona` `22:212`, `Foto/falla` `22:225`: 380×214 (16:9), mismo dibujo que los drawables `snap_sim_*` de la app, con el rótulo IMAGEN SIMULADA dibujado.

## Component sets (`02 Componentes`)

| Set | ID | Variantes |
|---|---|---|
| `StatusBadge` | `5:44` | state = ok / warning / error / stale / disabled × size = sm / lg |
| `CommandChip` | `5:67` | status = PENDIENTE / CONFIRMADA / VERIFICADA / RECHAZADA / SIN RESPUESTA |
| `SimTag` | `5:70` | componente único: borde rayado + "SIMULACIÓN" |
| `CommandButton` | `6:147` | kind = primary / secondary / destructive × state = enabled / disabled / pending / confirmed / rejected / noResponse |
| `HoldToConfirm` | `6:171` | progress = 0 / 50 / 100 × enabled = sí; progress = 0 × enabled = no |
| `StateStrip` | `7:272` | estado = EN BASE / EN RONDA / PAUSADA / REQUIERE DECISIÓN / DETENIDO / REGRESANDO / FALLA × needsAction = no / sí |
| `TelemetryReadout` | `7:373` | state = normal / warning / error / stale × withTrend = sí / no |
| `CheckpointRow` | `10:144` | state = pendiente / actual / verificado / con observación / salteado |
| `AlertRow` | `10:234` | severity = info / warning / critical × acknowledged = sí / no × conFoto = no; conFoto = sí en warning y critical (miniatura tocable) |
| `StaleDataBanner` | `10:268` | seconds = 4 (SIN DATOS) / 12 (DESCONECTADO) / 30 (+ política declarada) |
| `DecisionSheet` | `11:157` | opciones = 3 × foto = con foto / sin foto / datos viejos / recibiendo; opciones = 2 × foto = no aplica (propuesta de regreso por batería) |
| `RobotCard` | `11:200` | availability = disponible / ocupado / no compatible |
| `NavBar` | `11:319` | tab = supervision / misiones / alertas / historial |
| `FloorPlan` | `12:264` | floor = PB × escena = muestra / ronda-PC3 / base / completa; floor = 1° × escena = muestra / PC4 / PC5 / PC8 / regreso. `robot` anidado (RobotPose) |
| `TopBar` | `14:214` | tipo = principal (título + "Simulación") / secundaria (atrás + título + dato) |
| `FloorTab` | `14:221` | selected = sí / no (selector de piso; también filtros del Historial) |
| `AlertLine` | `14:242` | level = warning / critical / info (alerta compacta de Supervisión, como la app) |
| `Toggle` | `14:259` | estado = on / off (Panel de simulación) |

Capas de texto que se sobrescriben en instancias (por nombre): `etiqueta`, `motivo`, `estado`, `detalle`, `respuesta`, `conexion`, `valor`, `unidad`, `edad`, `tendencia`, `titulo`, `hora`, `codigo`, `nombre`, `pie`. En DecisionSheet la ilustración `imagen` se intercambia por `Foto/persona` o `Foto/falla`.

Decisiones visibles en los componentes:
- Detención operativa (software) siempre con "No reemplaza la parada física del robot."; se confirma manteniendo 1 s.
- Conexión siempre con antigüedad; con datos viejos el estado pasa a SIN DATOS / DESCONECTADO, nunca "Conectado".
- Progreso por puntos de control ("PC 3/8"), nunca porcentaje.
- Deshabilitado = contorno gris, etiqueta tachada y motivo escrito.

## Pantallas (`03 Pantallas`)

| Pantalla | ID | Nota |
|---|---|---|
| P02 Supervisión — en ronda PC 3/8 | `15:2` | `SupervisionPreviewData.enRonda` |
| P02b Supervisión — batería baja | `15:224` | Pausar pendiente |
| P02c Supervisión — incidente persona en PC5 | `15:447` | "Decidir cómo seguir" |
| P02d Supervisión — sin datos | `16:559` | captura 03 |
| P02e Supervisión — en base | `16:803` | |
| P02f Supervisión — PC 8/8 | `16:978` | |
| P02g Supervisión — ronda completada | `16:1191` | resumen + "Ver en historial" |
| P02h Supervisión — comando rechazado | `17:1227` | captura 05 |
| P02i Supervisión — enlace restaurado | `17:1454` | captura 04 |
| P02j Supervisión — detenido, recibiendo imagen | `23:2724` | hoja con foto = recibiendo |
| P01 Selección de robot | `18:1784` | |
| P04 Misiones — rutas y preflight | `18:1861` | |
| P04b Misiones — preflight con check fallido | `18:1972` | Iniciar ronda deshabilitado con motivo |
| P03 Telemetría | `18:2081` | |
| P05 Control | `19:2130` | deshabilitado con motivo, pendiente, rechazado, sin respuesta + registro |
| P06 Alertas | `19:2285` | alerta crítica con miniatura |
| P07 Historial | `19:2418` | |
| P08 Desconexión | `21:2434` | StaleDataBanner 30 + política declarada |
| P11 Reconexión y resincronización | `21:2570` | |
| P10 Decisión requerida — obstáculo PC4 | `17:1678` | captura 02: velo 70 % + DecisionSheet con foto |
| P12a Foto ampliada — obstáculo PC4 | `23:3001` | overlay, como el diálogo de la app |
| P12b Foto ampliada — persona PC5 | `23:3024` | overlay |
| P09 Panel de simulación | `21:2692` | borde rayado + SimTag |

Las pantallas de Supervisión siguen el orden de `SupervisionScreen.kt`. Las que pasan de 917 dp muestran el scroll completo, con la barra inferior al pie.

## Flujos

Flujo normal (`04`, sección `25:2`): N1 Selección `25:3` → N2 Supervisión en base `25:19` → N3 Misiones `25:53` → N4 Preflight `25:103` → N5 Supervisión PC 3/8 `25:153` → N6 PC 8/8 `25:195` → N7 Ronda completada `25:232` → N8 Historial `25:278`.

Flujo con falla (`05`, sección `26:2`): F1 Supervisión en ronda `26:3` → F2 detecta y se detiene `26:45` → F3 llega la foto, decisión requerida `26:83` → F3b foto ampliada (overlay) `26:121` → F4 regresando a base `26:128` → F5 desconexión con imagen del incidente `26:170` → F6 reconexión `26:198` → F7 en base `26:238` → F8 historial `26:272`.

## Prototipo (reacciones cargadas en el archivo)

- Flujo normal: Tomar control → N2; pestaña Misiones → N3; ruta "Ronda completa" → N4; Iniciar ronda → N5; N5 y N6 avanzan solos a los 3 s; Ver en historial → N8.
- Flujo con falla: F1 avanza a los 3 s; F2 a los 2 s; foto de F3 → F3b como overlay y Cerrar vuelve; "Regresar a la base" en F3 → F4 con mantener presionado 1 s (`MOUSE_DOWN`, delay 1); F4 avanza a los 2 s; Reintentar conexión → F6; Confirmar comandos pendientes → F7; pestaña Historial → F8.
- `03 Pantallas` (inicio "Navegación general" en P01): pestañas de la NavBar, "Simulación" → P09, lecturas de telemetría → P03, Más controles → P05, AlertLine → P06, atrás → volver, Decidir / Ver opciones → P10, foto de P10 → P12a y miniatura de P06 → P12b (overlay), Reintentar → P11, Confirmar → P02e, banner de P02d → P08.
- Las esperas automáticas están cargadas en milisegundos (3000 y 2000 ms).

## Exports (`design/exports/`)

- `00-portada.png`, `01-fundamentos.png`, `02-componentes.png`, `03-pantallas.png`, `04-flujo-normal.png`, `05-flujo-con-falla.png`, `06-mapa-navegacion.png`, `07-recorrido.png`.
- Detalles: `02-componente-statestrip.png`, `02-componente-commandbutton.png`, `02-componente-decisionsheet.png`, `02-componente-floorplan.png`.
- Una por pantalla en `pantallas/` (23 archivos, 412 px de ancho).

