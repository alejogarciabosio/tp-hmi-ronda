# Checklist de requisitos (R1–R24)

Revisión contra el enunciado al cierre de la entrega final (20/11/2026). "Cumple" indica que existe y se verificó en el repositorio, con tests o en el emulador.

| Req. | Qué pide | Dónde está | Estado |
|---|---|---|---|
| R1 | Caso de uso real: problema, cómo se resuelve hoy, quién usa el servicio, tarea del robot | `docs/entregas/entrega-final.md` §1, §3 y §4 | Cumple |
| R2 | Esquema del entorno o recorrido | `docs/arquitectura/recorrido.svg` (y `.png`), Figma `07-recorrido` | Cumple |
| R3 | Justificar G1/Go2 frente a una alternativa sin robot | entrega final §5 | Cumple |
| R4 | Sensores, movilidad, autonomía y restricciones; capacidades disponibles y adicionales | entrega final §6 (tabla) | Cumple |
| R5 | Roles, necesidades de información y situaciones de intervención humana | entrega final §3 | Cumple |
| R6 | Supervisar misiones, intervenir con comandos, gestionar contingencias | Pantallas Supervisión, Misiones, Control, Alertas y hoja de decisión | Cumple |
| R7 | Telemetría completa y tres variables del servicio | `core.model.RobotTelemetry`; Supervisión y Telemetría (puntos verificados, sala técnica, salidas de emergencia) | Cumple |
| R8 | Comandos: iniciar, pausar, reanudar, cancelar, punto seguro, detención operativa, reconocer alertas | `CommandType`; Misiones, Control, Supervisión y Alertas | Cumple |
| R9 | Habilitación, respuesta simulada y estado pendiente / confirmada / rechazada / sin respuesta | `CommandPolicy`, `CommandTracker`, `CommandButton`; `CommandButtonUiTest` y `ControlViewModelTest` | Cumple |
| R10 | Detención en la app no es parada física; enviada no es ejecutada | `OperationalStopTexts.NOTE` junto a cada botón de detención; "Confirmada" se distingue de "Verificada"; la detención no depende del control exclusivo (`ControlSession`, D11) | Cumple (`HoldToConfirmUiTest`) |
| R11 | Figma navegable con mapa, componentes y estados visuales | Figma (enlace en `design/FIGMA.md`), `design/exports/` | Cumple (lo hizo Alejo) |
| R12 | Figma cubre las ocho pantallas pedidas | Figma página `03 Pantallas` | Cumple |
| R13 | Figma con flujo normal y flujo con falla | Figma páginas `04` y `05` | Cumple |
| R14 | Kotlin, Compose, MVVM, ViewModel y StateFlow; la UI no accede al simulador | `app/`; `core.sim` solo se importa en `AppContainer` dentro de `app/src/main`; el único otro import está en `SupervisionDemoRecorder`, un test que graba la demo y no es UI (verificado con búsqueda en `app/src`) | Cumple |
| R15 | Repository que desacopla telemetría, comandos y eventos | `core.repository.RobotRepository` | Cumple |
| R16 | Modelos, Repository, ViewModel y componentes separados | Paquetes `core.model`, `core.repository`, `ui.<pantalla>`, `ui.components` | Cumple |
| R17 | Historial persistido con Room | `app/data/local`, pantalla Historial; `HistoryDaoTest` | Cumple |
| R18 | Funciona sin robot, servidor ni internet | El manifest no pide permisos de red; todo corre sobre el simulador local | Cumple |
| R19 | Simulador: batería, telemetría, misiones, alertas, desconexión y rechazos | `core.sim`; Panel de simulación | Cumple |
| R20 | Contrato JSON documentado, aclarando que no es oficial de Unitree | `docs/contrato/` | Cumple |
| R21 | Cinco escenarios demostrables | Presets del panel, tests de escenarios en `core`, tests de punta a punta en `app` (`e2e/`), capturas en `docs/evidencias/capturas/` y transcripciones en `docs/evidencias/transcripciones/` | Cumple |
| R22 | Cinco riesgos con medidas | `docs/riesgos.md` (7 riesgos, RG1 a RG7) y entrega final §13 | Cumple |
| R23 | Preentrega | Ya no se entrega por separado: su contenido es la base de la entrega final | No aplica |
| R24 | Entrega final completa | `docs/entregas/entrega-final.md` (y PDF), APK en `docs/entregas/apk/ronda-debug.apk`, video en `docs/evidencias/video/demo-ronda.mp4`, Figma en `design/FIGMA.md` | Cumple |

## Accesibilidad

- **Área táctil:** todo elemento que se puede tocar mide al menos 48 × 48 dp. `PantallasAccesibilidadTest` lo verifica en nueve estados de pantalla (Selección, Supervisión con y sin datos, Telemetría, Misiones, Control, Alertas, Desconexión y Panel de simulación).
- **Confirmación de órdenes críticas:** Cancelar, Regresar a la base y la Detención operativa se confirman manteniendo presionado 1 s (`HoldToConfirm`); lo cubre `HoldToConfirmUiTest`.
- **Lector de pantalla:** cada elemento tocable tiene un texto o una descripción (lo verifica el mismo test). El estado de una orden se lee en castellano ("pendiente", "sin respuesta"). La detención operativa también se confirma con la acción de mantener presionado de TalkBack.
- **Contraste (WCAG 2.1):** `ContrasteTest`. El texto principal alcanza 14,9:1, el secundario 6,9:1, la advertencia 9,3:1, el error 4,7:1 y el estado normal 6,5:1. El gris de dato viejo o deshabilitado (`#908B7C`) da 5,46:1 sobre el fondo y 4,73:1 sobre la tarjeta elegida, y queda más tenue que el texto secundario. Además nunca va solo: lo acompañan el símbolo de estado, el tachado y el motivo escrito.
- **No solo color:** cada estado combina color, forma (`StateSymbol`) y texto. La advertencia lleva además franjas diagonales.
