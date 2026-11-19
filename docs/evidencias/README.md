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
