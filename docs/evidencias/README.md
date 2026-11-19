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
