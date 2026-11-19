# Guion del video de demostración

Duración objetivo: 7 a 8 minutos. Se graba la app instalada en el emulador (Pixel, API 35) con la grabación de pantalla de Android Studio o `adb shell screenrecord`. La voz en off va en castellano. Al empezar se dice una vez que todo corre sobre el simulador local, sin robot, servidor ni internet, y que el contrato JSON es una propuesta del grupo y no el protocolo de Unitree.

El simulador corre a velocidad real (una ronda completa dura unos 8 minutos y medio, ver L3). Para no esperar, cada escenario arranca la ronda y usa el panel de simulación para llegar a la situación. Eso se dice en voz alta la primera vez.

## 0. Apertura (0:00–0:40)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 0.1 | Selección de robot: RONDA-01 disponible, RONDA-03 en uso, RONDA-02 no compatible | "Es la garita de un edificio de oficinas a las 3 de la mañana. El operador elige el robot y toma el control: nadie más le puede dar órdenes." |
| 0.2 | Tocar "Tomar control" en RONDA-01 y llegar a Supervisión en base | "La pantalla principal muestra el estado, el progreso por puntos de control y el plano. Ningún dato se muestra como vigente si no lo es." |

## 1. Ronda normal (0:40–2:00)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 1.1 | Panel de simulación, "1 · Ronda normal" | "Este panel es solo para la demo: fuerza situaciones para mostrar cómo responde la interfaz." |
| 1.2 | Misiones: Ronda completa, preflight en verde, "Iniciar" | "Antes de salir, la app verifica batería para la ruta elegida, enlace, alertas críticas y que el control sea nuestro." |
| 1.3 | Orden PENDIENTE, luego confirmada; Supervisión EN RONDA avanzando de PC1 a PC2 | "Una orden enviada no es una orden ejecutada: primero queda pendiente, después el robot la confirma y recién la telemetría la verifica." |
| 1.4 | Telemetría: lecturas con su antigüedad, puntos verificados, sala técnica y salidas | "Además del robot, se ven tres variables del servicio: puntos verificados, temperatura de la sala técnica y estado de las salidas de emergencia." |
| 1.5 | Historial con una misión anterior | "Las misiones y los eventos quedan guardados en el teléfono con Room, 30 días." |

## 2. Batería baja (2:00–3:10)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 2.1 | Panel: "2 · Batería baja" (40 %, celda degradada); Misiones muestra que alcanza justo | "El robot sale con el mínimo operativo de 40 % y una celda degradada que gasta seis veces más." |
| 2.2 | Iniciar la ronda; en el panel, "Batería −10 %" hasta bajar de 25 % | "Para no esperar la descarga, bajamos la batería desde el panel." |
| 2.3 | Alerta de batería baja con la acción destacada "Regresar a base" | "Al cruzar 25 % la app avisa y destaca la acción: regresar mientras alcance." |
| 2.4 | Bajar de 15 %: el robot propone el regreso y el operador confirma | "A 15 % el robot propone volver. La máquina propone y la persona decide." |

## 3. Desconexión (3:10–4:30)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 3.1 | Panel: "3 · Desconexión"; iniciar la ronda | |
| 3.2 | Panel: "Cortar enlace 15 s"; volver a Supervisión | "Se corta el enlace. La app no muestra Conectado por tener datos viejos en memoria: dice 'sin datos hace N s', grisa los valores y deshabilita las órdenes con el motivo." |
| 3.3 | Control: órdenes deshabilitadas con el motivo, la detención operativa sigue disponible | "Sin datos, las órdenes se deshabilitan y dicen por qué. Solo queda la detención operativa." |
| 3.4 | Tocar el banner y abrir Desconexión: último dato conocido y política del robot sin enlace | "El robot, según el supuesto declarado, se detiene y a los 60 s vuelve solo a la base." |
| 3.5 | El enlace vuelve: eventos atrasados con su hora original, reanudar | "Al volver, la app muestra lo que pasó durante el corte con la hora en que ocurrió." |

## 4. Incidente: obstáculo (4:30–5:40)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 4.1 | Panel: "4 · Obstáculo"; iniciar la ronda; "Inyectar obstáculo" (en la ronda real salta en el PC4) | |
| 4.2 | Supervisión REQUIERE DECISIÓN; hoja con la foto "IMAGEN SIMULADA" y tres opciones | "El robot no puede pasar: se detiene a 2 m y manda una foto de la cámara frontal. Ofrece esperar 30 s, saltar el punto o regresar." |
| 4.3 | Elegir "Saltar el PC4": el punto queda con observación y la ronda sigue | "El punto saltado queda registrado con observación, no como verificado." |
| 4.4 | Control: "Cancelar misión" con un toque corto (no pasa nada), después mantener 1 s | "Cancelar, regresar a la base y detener piden mantener presionado: un toque accidental no alcanza." |
| 4.5 | Detención operativa (software) con su aclaración | "Este botón es una detención por software y no reemplaza la parada física del robot." |

## 5. Persona en zona restringida (5:40–6:50)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 5.1 | Panel: "5 · Persona en zona restringida"; iniciar; "Inyectar persona" (en la ronda real salta en el PC5, sala técnica) | |
| 5.2 | Alerta crítica y hoja de decisión con la foto | "El detector puede equivocarse: una sombra o un reflejo. Por eso el robot no declara un intruso: se detiene a 2 m, se identifica por voz y espera." |
| 5.3 | Opciones: persona autorizada, enviar vigilante, continuar; elegir "Enviar vigilante". La hoja se cierra y arriba aparece "Vigilante en camino · el robot espera tu orden para seguir" | "La persona que mira la foto decide. El robot espera pausado hasta que el operador lo reanude." |
| 5.4 | Historial: el evento sin foto y la orden "Reconocer alerta · Enviar vigilante" | "La foto se usa para decidir y no se guarda. El registro dice solo qué pasó, dónde y qué decidió el operador." |

## 6. Cierre (6:50–7:30)

| Toma | Qué se ve | Qué se dice |
|---|---|---|
| 6.1 | Panel: "Rechazar próximo comando" y después "Pausar" rechazado con su motivo | "Si el robot rechaza una orden, la app muestra el motivo." |
| 6.2 | Panel: "Demorar respuestas 5 s"; "Reanudar" queda PENDIENTE y pasa a SIN RESPUESTA | "Si la respuesta no llega a tiempo, la app no sabe si la orden se ejecutó, y lo dice así." |
| 6.3 | Supervisión en ronda | "Arquitectura MVVM: la interfaz solo observa el estado y el repositorio separa la app del simulador. Reemplazar el simulador por un robot real es escribir otro transporte, sin tocar las pantallas." |

## Antes de grabar

- Instalar el APK limpio (`adb install -r app-debug.apk`) o borrar los datos de la app, así el historial arranca vacío salvo lo que se genere en el video.
- Poner el emulador en vertical, sin notificaciones.
- Para cada escenario, cargarlo desde el panel antes de iniciar la ronda: cargar un escenario reinicia el robot en la base.
