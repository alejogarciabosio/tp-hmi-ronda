# Evidencias

## Transcripciones de los cinco escenarios

`transcripciones/` tiene un archivo de texto por escenario del plan (ronda normal, batería baja, desconexión, obstáculo, persona en zona restringida). Cada línea lleva la hora virtual desde el inicio del escenario y uno de estos registros:

- `TELEMETRIA`: resumen de lo que ve el operador (estado, punto de control, batería, zona, alertas sin reconocer).
- `COMANDO`: cada orden con su estado: pendiente, confirmada, rechazada, sin respuesta o verificada. Confirmada significa que el robot aceptó la orden; verificada, que la telemetría refleja el efecto.
- `EVENTO`: alertas y decisiones requeridas, con la hora original de emisión (importa tras una desconexión).
- `CONEXION`: cambios del estado del enlace.
- `OPERADOR` y `PANEL DE SIMULACION`: lo que hace la persona en la app y en el panel de simulación.

Son la salida de `ScenarioTranscriptsTest` (en `core/src/test/.../scenarios/`), que corre cada escenario de punta a punta contra el repositorio simulado con tiempo virtual y semilla fija: el resultado es el mismo en cada corrida, sin esperas reales.

### Cómo regenerarlas

```bash
./gradlew :core:scenarioTranscripts
```

La tarea corre solo esa clase de tests y reescribe los archivos de `transcripciones/`. Con `./gradlew :core:test` los escenarios se ejecutan y se verifican igual, pero no se escribe nada. Si cambia el simulador, hay que regenerar y commitear las transcripciones.

Las transcripciones salen del simulador: demuestran el comportamiento de la app y el contrato propio del grupo, no el de un robot real.

## Video y capturas de la pantalla Supervisión

`video/supervision-demo.mp4` (unos 50 s, 540 px de ancho, H.264 yuv420p) muestra la pantalla Supervisión real: el ViewModel real conectado al repositorio simulado, con tiempo virtual y semilla fija (42). Guion con el rótulo del paso arriba:

1. Ronda normal: avanza hasta el PC 3.
2. Obstáculo: se abre la hoja de decisión y se elige "Saltar el PC4".
3. Enlace cortado: "Desconectado · hace N s", valores en estado último conocido, nunca "Conectado".
4. Enlace restaurado: el robot quedó detenido y el operador reanuda.
5. Rechazo forzado de "Pausar", con su motivo visible en Órdenes.

`capturas/supervision-*.png` son los cuadros clave de cada paso.

### Cómo regenerarlos

```bash
./gradlew :app:recordDemo
```

La tarea corre solo `SupervisionDemoRecorder` (Robolectric con gráficos nativos, sin emulador), escribe los cuadros en `app/build/demo-frames` y `docs/evidencias/armar-demo.sh` arma el MP4 y copia las capturas con ffmpeg (hay que tenerlo instalado). Ese test está excluido de `:app:testDebugUnitTest`. Es la app corriendo contra el simulador, no contra un robot real.

## Capturas de la app completa

`capturas/app-*.png` (una por pantalla) y `capturas/escenario-*.png` (el momento clave de cada escenario) salen de la app real: ViewModels reales contra el simulador, con tiempo virtual y semilla fija (42), renderizadas con Robolectric (gráficos nativos, 411x891 dp a xxhdpi) sin emulador. Cada pantalla se captura en su primer viewport: las que scrollean (Misiones, Control, Desconexión, Panel de simulación) quedan cortadas abajo, como en el teléfono. Además de la tabla, `app-02-supervision.png` muestra Supervisión en ronda.

### Cómo regenerarlas

```bash
./gradlew :app:recordCapturas
```

La tarea corre solo `CapturasRecorder` y reescribe los PNG en `capturas/`. Ese test está excluido de `:app:testDebugUnitTest`. Es la app contra el simulador, no contra un robot real. Para el APK real en un emulador se pueden tomar las mismas con `adb exec-out screencap -p > archivo.png`.

| Archivo | Cómo llegar |
|---|---|
| `app-01-seleccion.png` | Al abrir la app |
| `app-03-telemetria.png` | Supervisión en ronda, tocar una lectura |
| `app-04-misiones.png` | Pestaña Misiones con el robot en base |
| `app-04b-misiones-preflight-fallido.png` | Panel: "Batería −10 %" hasta 30 %, volver a Misiones |
| `app-05-control.png` | En ronda, "Más controles" |
| `app-06-alertas.png` | Con una alerta activa (por ejemplo, después de inyectar un obstáculo) |
| `app-07-historial.png` | Después de terminar o cancelar una ronda |
| `app-08-desconexion.png` | Panel: "Cortar enlace", tocar el aviso de sin datos en Supervisión |
| `app-09-panel-simulacion.png` | "Simulación", arriba a la derecha |
| `escenario-1-ronda-normal.png` | Escenario 1, Supervisión en ronda |
| `escenario-2-bateria-baja.png` | Escenario 2, alerta de batería baja con "Regresar a base" |
| `escenario-3-desconexion.png` | Escenario 3, durante el corte de 15 s |
| `escenario-4-obstaculo.png` | Escenario 4, hoja de decisión con la foto |
| `escenario-5-persona.png` | Escenario 5, hoja de decisión con la foto |

El guion de cada escenario está en `docs/entregas/guion-video.md`.
