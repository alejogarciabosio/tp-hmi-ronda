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

## D7. `messageId` cortos en lugar de UUID

- **Contexto.** El plan §6 proponía UUID en `messageId`.
- **Decisión.** Los ejemplos y el simulador usan ids cortos con prefijo por emisor (`c-0001`, `r-0002`, `t-000412`, `e-0011`).
- **Consecuencias.** Se leen mejor en las evidencias y en los logs. Son únicos dentro de una sesión del simulador; un gateway real, con varios emisores y reinicios, debería generar UUID. El contrato no exige un formato, solo unicidad.

## Limitaciones

### L1. Escalones mayores a 16 cm (riesgo R7)

El Go2 declara un escalón máximo de unos 16 cm. Esto no se puede simular: depende del edificio real. Antes de habilitar la ruta hay que relevar la contrahuella y el material de las dos escaleras; si alguna supera el límite, la ronda se limita a un piso. La app solo muestra el modo de movimiento ESCALERA. Ver `docs/riesgos.md`, R7.

### L2. Contrato JSON propio

El contrato de `docs/contrato/` es una propuesta del grupo y no el protocolo oficial de Unitree. Un gateway real traduciría estos mensajes a llamadas del SDK.
