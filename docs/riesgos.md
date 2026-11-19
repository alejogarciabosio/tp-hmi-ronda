# Riesgos operativos y de interacción

Servicio: ronda nocturna con un Unitree Go2 EDU supervisado desde la garita con la app RONDA. Los límites del robot citados salen de `docs/fuentes.md` (GO2-8 es el manual de usuario).

Dos aclaraciones que valen para todo el documento:

- La "Detención operativa (software)" de la app es una orden simulada enviada al robot. **No reemplaza la parada de emergencia física** ni el corte de energía del robot.
- Una orden enviada no es una orden ejecutada. La app distingue enviada, aceptada y verificada por telemetría.

Cada riesgo se describe con causa, consecuencia, medida (de interfaz, de lógica o de procedimiento) y la forma de verificar que la medida existe y funciona. "Verificación" remite a escenarios del simulador (E1 a E5, ver preentrega §9) o a tests de `core`; los nombres de tests son los previstos y se ajustan cuando exista el código.

## Resumen

| # | Riesgo | Severidad | Probabilidad | Medida principal | Escenario de prueba |
|---|---|---|---|---|---|
| R1 | Colisión o susto a una persona cercana | Alta | Media | Velocidad reducida, aviso por voz, detención rápida | E5 |
| R2 | Orden accidental (cancelar o detener por toque involuntario) | Media | Alta | Mantener presionado 1 s, estado PENDIENTE visible | E1, E3 |
| R3 | Pérdida de conexión | Alta | Media | Datos marcados como antiguos, comandos deshabilitados, resincronización | E3 |
| R4 | Batería insuficiente para volver a la base | Alta | Media | Bloqueo de inicio, cálculo de "alcanza para volver" | E2 |
| R5 | Privacidad de las personas detectadas | Media | Alta | No se guardan imágenes, registro mínimo, retención acotada | E5 |
| R6 | Dos operadores con órdenes contradictorias | Media | Baja | Sesión exclusiva por robot | selección de robot |
| R7 | Escalones fuera del rango del robot | Alta | A confirmar | Relevamiento previo, ruta solo por tramos validados | procedimiento |

Severidad y probabilidad son estimaciones del grupo para el caso de estudio, no mediciones.

---

## R1. Colisión o susto a una persona cercana

- **Descripción.** Personal de limpieza o personas rezagadas comparten pasillos con el robot. Un cuadrúpedo de unos 15 kg que aparece en un pasillo oscuro puede asustar, o la persona puede tropezar con él.
- **Causa.** El robot se mueve sin que la persona sepa que está ahí; la evasión de obstáculos del Go2 funciona solo hacia adelante (GO2-8) y el manual pide mantener al robot a 2 m de multitudes y obstáculos.
- **Consecuencia.** Caída o golpe de la persona, daño al robot, pérdida de confianza en el sistema.
- **Medidas.**
  - Procedimiento: ronda posterior a las 23:30, con aviso previo al personal de limpieza; sin ronda con el robot si hay un evento en el edificio.
  - Lógica: velocidad reducida en pasillos comunes; ante una persona detectada, el robot se detiene a 2 m y no avanza hasta recibir decisión (escenario 5).
  - Interfaz: indicador "personas cerca" en la franja de estado; "Detención operativa (software)" siempre visible; la HMI aclara que no reemplaza la parada física.
  - HRI: mensaje de voz identificatorio del robot ("Soy el robot de ronda de seguridad. Estoy avisando a vigilancia."). La capacidad de voz está declarada por el fabricante (GO2-1); los mensajes propios requieren desarrollo.
- **Verificación.** Escenario E5 (persona en sala técnica): el estado pasa a REQUIERE_DECISION y el simulador no avanza de PC5 hasta que el operador decide. Test de `core` que comprueba que con persona detectada el robot queda detenido. Revisión del Figma: botón de detención visible en todas las pantallas de supervisión.

## R2. Orden accidental

- **Descripción.** El operador cancela la misión, pide regreso o detiene al robot por un toque involuntario (garita con poca luz, guantes, celular en la mano).
- **Causa.** Botones de consecuencia física cercanos entre sí o de un solo toque.
- **Consecuencia.** Ronda interrumpida sin necesidad; o, al revés, una orden de movimiento enviada sin querer.
- **Medidas.**
  - Interfaz: Cancelar, Regreso a punto seguro y Detención operativa requieren **mantener presionado 1 s** (barra de progreso), sin diálogos de confirmación repetitivos. Iniciar misión muestra un resumen previo (checklist de condiciones).
  - Interfaz: los botones destructivos están separados físicamente de los de uso habitual y con otro tratamiento visual (rayado de señalización).
  - Lógica: un solo comando de misión pendiente a la vez; la matriz de habilitación (`CommandPolicy`) deshabilita lo que no corresponde con el motivo escrito.
  - Recuperación: tras una detención operativa, "Reanudar" queda disponible si no hay alerta crítica sin reconocer.
- **Verificación.** Tests de `CommandPolicy` por tabla de casos (habilitado/deshabilitado y motivo por cada comando y estado). Prueba manual en el APK: un toque corto sobre un botón destructivo no envía nada. Estado PENDIENTE visible en E1 y E3.

## R3. Pérdida de conexión

- **Descripción.** El enlace entre la app y el robot (Wi-Fi; el Go2 también tiene 4G, GO2-1) se corta o se degrada, y la pantalla sigue mostrando el último valor como si fuera actual.
- **Causa.** Zonas sin cobertura (escaleras, sala técnica), interferencia (el manual desaconseja ambientes con Wi-Fi congestionado, GO2-8), caída del gateway.
- **Consecuencia.** El operador decide con datos viejos o cree que una orden se ejecutó cuando no llegó.
- **Medidas.**
  - Lógica: estado de conexión derivado de la antigüedad del último mensaje (más de 1,5 s degradado, más de 3 s sin datos, más de 10 s desconectado). Nunca se muestra "Conectado" solo porque hay datos en memoria.
  - Interfaz: valores en gris con texto de "dato antiguo" y hora del último dato; banner "Sin datos desde hace X s"; comandos deshabilitados con motivo, salvo la detención operativa, que se envía igual y puede quedar SIN_RESPUESTA.
  - Procedimiento: se supone que el robot ante pérdida de enlace se detiene, espera 60 s y regresa a la base. **Es un supuesto del caso de estudio, no una garantía** que ofrezca la app ni una función verificada en las fuentes. Debe configurarse y probarse con el robot real.
  - Recuperación: al reconectar se resincroniza, se listan los eventos ocurridos durante el corte con su hora original y se pide confirmar de nuevo los comandos pendientes.
- **Verificación.** Escenario E3 (corte de 15 s forzado desde el panel de simulación). Tests de `ConnectionEvaluator` con tiempo virtual en los umbrales. Test: comando enviado durante el corte termina SIN_RESPUESTA a los 4 s.

## R4. Batería insuficiente para volver a la base

- **Descripción.** El robot se queda sin energía lejos de la base, en un piso distinto o en una escalera.
- **Causa.** Se subestima el consumo (el fabricante indica 2-4 h con 15000 mAh, equivalentes a 432 Wh, medidas sin carga y alternando reposo y marcha; baja con velocidad alta, carga, posturas bruscas y terreno irregular, GO2-1, GO2-8 y GO2-9; no hay medición independiente). Los coeficientes del simulador y su fundamento están en `docs/supuestos-simulacion.md`. El manual indica detener el robot cuando queda un solo bloque de batería.
- **Consecuencia.** Caída en una escalera, robot detenido en un lugar de paso, ronda sin terminar.
- **Medidas.**
  - Lógica: no se inicia una ronda con menos de 40 % de batería ni si no alcanza para la ruta; "alcanza para volver" se calcula con la distancia restante.
  - Interfaz: advertencia a 25 %, con acción destacada "Regresar a base"; a 15 % el robot propone regreso automático y el operador lo confirma. "Continuar igual" se deshabilita si no alcanza.
  - Procedimiento: punto seguro alternativo en el descanso de la escalera E si la base queda lejos. La base de carga es compatible con el EDU (GO2-1) y el manual dice que la EDU soporta carga autónoma (GO2-8); no se pudo confirmar una llamada equivalente en el SDK, y los antecedentes de patrullaje con Go2 resuelven el acople por fuera del firmware (ver `docs/fuentes.md`, ampliación del 2026-11-20).
- **Verificación.** Escenario E2 (arranca con 34 %, cruza 25 %). Tests de `BatteryPolicy` y de `CommandPolicy` (iniciar con batería insuficiente devuelve rechazo con motivo).

## R5. Privacidad de las personas detectadas

- **Descripción.** El robot lleva cámara y puede registrar a personas autorizadas o no (estudiantes, docentes, personal).
- **Causa.** La detección de personas no figura como función nativa del Go2 en las fuentes leídas; habría que desarrollarla sobre la cámara frontal y el LiDAR (un proyecto comunitario lo hace con un detector YOLO propio, GO2-12). Guardar imágenes crearía datos personales sin necesidad.
- **Consecuencia.** Exposición de datos personales, incumplimiento de normas de protección de datos, rechazo del servicio.
- **Medidas.**
  - Lógica: el contrato JSON (propuesta propia del grupo, no el protocolo oficial de Unitree) transporta una única foto por incidente, solo para que el operador decida (D9). La foto **se muestra y no se guarda**: no entra en el historial ni en la base local, y se descarta al resolverse el incidente. El evento registra solo "persona detectada, PC5, 02:14, decisión del operador". No hay grabación continua ni video.
  - Procedimiento: retención de eventos de 30 días; acceso al historial solo para el rol supervisor; aviso en el edificio de que existe un robot de ronda. El plazo y los roles son propuestas del grupo, a validar con el área legal del edificio.
  - Interfaz: la foto aparece solo en la hoja de decisión del incidente activo, con su hora de captura; el historial y la pantalla de alertas resueltas no muestran imágenes; no hay función para guardar ni compartir la foto.
- **Verificación.** Revisión del esquema Room (ninguna columna de imagen) y del contrato JSON propuesto, que no es el protocolo oficial de Unitree (la foto viaja solo en el evento del incidente). E5: el registro del evento contiene solo los campos listados y la foto deja de mostrarse una vez tomada la decisión.

## R6. Dos operadores con órdenes contradictorias

- **Descripción.** Dos personas con la app abierta envían órdenes distintas al mismo robot.
- **Causa.** Sin noción de quién tiene el control.
- **Consecuencia.** Estados inconsistentes (uno pausa, otro reanuda).
- **Medidas.** Sesión exclusiva por robot: en la selección se muestra "RONDA-01 controlado por: Garita PB" y la acción "Tomar control"; el otro cliente queda en solo lectura. El manual además advierte no mezclar comandos de alto nivel con control remoto (GO2-8), lo que refuerza la regla de un único origen de órdenes.
- **Verificación.** Revisión de la pantalla de selección en el Figma; en la app, el robot muestra quién tiene el control.

## R7. Escalones fuera del rango del robot

- **Descripción.** El Go2 declara un escalón máximo de aproximadamente 16 cm y el manual no recomienda subir escalones más altos (GO2-1, GO2-8). La ronda usa dos escaleras.
- **Causa.** Las escaleras de un edificio real pueden tener contrahuellas mayores, o pisos resbaladizos o mojados (el manual advierte sobre baldosa lisa y suelos mojados).
- **Consecuencia.** Caída del robot en una escalera, daño al equipo y a las personas debajo.
- **Medidas.** Procedimiento: relevar altura de contrahuella y material de las dos escaleras antes de habilitar la ruta; si alguna supera el límite, la ronda se limita a un piso. Lógica: velocidad reducida en el modo ESCALERA. Interfaz: el modo de movimiento "ESCALERA" se muestra en la telemetría.
- **Verificación.** Es un riesgo **sin simulación posible**: se resuelve con el relevamiento del edificio. Queda como limitación en `docs/decisiones.md`. La app solo muestra el modo de movimiento.
