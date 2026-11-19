# Supuestos de la simulación: batería y capacidades del Go2

Qué se sabe del Unitree Go2 EDU, qué se calcula a partir de eso y qué queda como supuesto del grupo. Las fuentes (ID entre paréntesis) están en `docs/fuentes.md`, ampliación del 2026-11-19. Los coeficientes de este documento son del **simulador**; no son mediciones del Go2 ni valores publicados por Unitree.

## 1. Capacidades del Go2 relevantes para el caso

| Capacidad | Estado | Detalle |
|---|---|---|
| Detección de personas nativa | No confirmada | Unitree lista "seguimiento de persona (ISS 2.0)" en Pro/X/EDU (GO2-1), pero junto al posicionamiento inalámbrico y con referencia a la altura del control remoto (GO2-8): parece un seguimiento apoyado en el control, no un detector visual. Ninguna fuente describe un servicio de detección de personas en el SDK. Un proyecto comunitario usa YOLO propio sobre la cámara frontal (GO2-12). En el caso, la detección es una capacidad a desarrollar. |
| Carga autónoma | Parcialmente confirmada | La EDU es compatible con la pila de carga (GO2-1) y el manual dice que soporta carga autónoma planificando la ruta de carga (GO2-8). No se pudo leer la documentación del SDK (GO2-5) para saber si hay una llamada de acople. El proyecto GO2-12 lo resuelve con Nav2 y AprilTag. Spot y ANYmal lo traen como función de producto (SPOT-1, ANY-1). |
| Evasión de obstáculos | Confirmada, solo frontal | El LiDAR 4D L2 cubre 360 x 96 ° con mínimo de 0,05 m (GO2-1), pero el manual indica que la evasión soporta únicamente obstáculos hacia adelante (GO2-8). |
| Escalón máximo | Confirmado | ~16 cm; el manual no recomienda subir escaleras de más de 16 cm (GO2-1, GO2-8). Air: ~15 cm. |
| Ángulo de escalera | No figura | Solo hay pendiente máxima: 40 ° (Pro/X/EDU) y 30 ° (Air) (GO2-1). Una escalera de edificio tiene en general un ángulo distinto; se resuelve relevando el edificio (riesgo R7). |
| Ambiente | Confirmado | 5 a 35 °C, buen clima, sin grado IP; 2 m de distancia a multitudes y obstáculos (GO2-7, GO2-8). |
| Velocidad | Confirmada | 0-3,7 m/s en EDU (máx. ~5 m/s de laboratorio) (GO2-1). Para una ronda interior se supone 0,8 m/s (valor del simulador, `WALK_SPEED_MPS`); como referencia, ANYmal camina a 0,75 m/s (ANY-1) y Spot llega a 1,6 m/s (SPOT-1). |
| Energía de la batería EDU | Confirmada por distribuidores | 15000 mAh x 28,8 V = 432 Wh (GO2-9, GO2-10). Unitree publica solo mAh y el rango 28-33,6 V (GO2-1). |
| Autonomía | Declarada, sin medición independiente | 2-4 h "sin carga, de pie y caminando de forma alternada" (GO2-1, GO2-8). No se publica distancia por carga ni consumo en reposo. |

## 2. Modelo de batería propuesto

### 2.1 Datos de partida

- Energía: E = 432 Wh.
- Autonomía declarada: 2 h a 4 h, mezcla de reposo de pie y marcha (GO2-8).
- Potencia media implícita: 432 Wh / 2 h = **216 W** (extremo desfavorable) y 432 Wh / 4 h = **108 W** (extremo favorable). El pico de 3000 W (GO2-1) no es un consumo sostenido.
- Velocidad de marcha de la ronda: v = 0,8 m/s (supuesto, ver 1).

### 2.2 Modelo

Es el que ya usa `RobotSimulator.updateBattery`: consumo = (consumo base por segundo + velocidad x consumo por metro) x factor de desgaste. Se fijan los dos parámetros con los dos extremos de la autonomía:

1. **Reposo (parado, encendido):** se toma la potencia del extremo favorable, 108 W. Es un supuesto: el Go2 parado sostiene su peso con los motores y no hay medición publicada. En porcentaje de la batería: 108 W / 432 Wh = 25 %/h = 0,42 %/min = 0,0069 %/s. Se redondea a **0,007 %/s**.
2. **Marcha:** se toma el extremo desfavorable, 216 W a 0,8 m/s. El excedente sobre el reposo es 216 - 108 = 108 W, es decir 108 J/s / 0,8 m/s = 135 J/m = 0,0375 Wh/m = 0,0087 %/m. Se suma un 15 % de margen por escaleras, giros, baldosa lisa y paradas (el manual dice que la autonomía baja con marcha rápida y cuerpo bajo, GO2-8) y se redondea a **0,010 %/m** (0,043 Wh/m).
3. **Control de coherencia:** parado, 100 / 0,42 %/min = 3,97 h (límite superior declarado). Caminando de continuo a 0,8 m/s, el consumo es 0,42 + 0,8 x 60 x 0,010 = 0,90 %/min, o sea 1,85 h, algo por debajo de las 2 h declaradas: el modelo es conservador a propósito.

### 2.3 Costo de la ronda completa

El recorrido calculado con `BuildingLayout` (base, PC1 a PC8 y regreso, con 12 m extra por cada cambio de piso) mide **293 m**, no ~400 m como figuraba en las notas. Con marcha a 0,8 m/s (0,4 m/s en los tramos de escalera) son ~396 s de marcha, más 8 paradas de 30 s (supuesto del grupo para detención y verificación, 240 s):

| Concepto | Cuenta | Resultado |
|---|---|---|
| Por distancia | 293 m x 0,010 %/m | 2,9 % |
| Reposo durante la marcha | 396 s x 0,007 %/s | 2,8 % |
| Paradas | 240 s x 0,007 %/s | 1,7 % |
| **Total de la ronda** | | **~7,4 % (~32 Wh, unos 11 min)** |

Por punto de control, incluyendo el tramo hasta llegarle y la parada: 7,4 / 8 = 0,92, se redondea a **1,0 %**.

### 2.4 Comparación con los coeficientes actuales

| Parámetro | Actual | Propuesto | Cómo se obtiene |
|---|---|---|---|
| Consumo por metro | 0,08 %/m | **0,010 %/m** | 0,0375 Wh/m + 15 % de margen, sobre 432 Wh |
| Consumo detenido | 0,003 %/s (0,18 %/min) | **0,007 %/s (0,42 %/min)** | 108 W (autonomía de 4 h) sobre 432 Wh |
| Costo por punto de control (usado para el mínimo de salida) | 3,5 % | **1,0 %** | 7,4 % de la ronda / 8 PC |
| Reserva al llegar a la base | 10 % | **15 %** | ver 2.5 |

Qué dicen los valores actuales: 0,08 %/m a 0,8 m/s equivale a 0,064 %/s = 3,84 %/min, o sea unos **25 min de autonomía caminando** (1,04 kW de potencia media), contra las 2-4 h declaradas. Es unas 5 veces la potencia media del extremo desfavorable (216 W). En reposo, 0,18 %/min equivale a 47 W y 9 h de autonomía parado, por encima del extremo favorable (4 h): los valores actuales subestiman el reposo y sobrestiman la marcha. En la ronda completa del simulador los 293 m consumen hoy ~23 % más ~2 % de reposo, más de 3 veces el ~7,4 % calculado.

### 2.5 Reserva y umbral de regreso

Lo que se encontró sobre la práctica habitual:

- No hay una norma ni un valor universal. La documentación leída (AMR-2) solo dice que el robot vigila su carga y decide cuándo ir al cargador, sin porcentajes.
- Open-RMF, el framework abierto de flotas de robots, trae en su configuración de ejemplo `recharge_threshold: 0.10`, "nivel de batería por debajo del cual los robots de la flota no operan" (AMR-1). Es un ejemplo de configuración, no una recomendación.
- Spot y ANYmal van solos al dock con batería baja y retoman la misión al cargar (SPOT-1, ANY-1); ninguno publica el porcentaje de disparo.
- El manual del Go2 pide detener y apagar el robot cuando queda una sola barra y el robot se agacha solo a los 10 minutos del aviso (GO2-8).
- Una búsqueda devolvió un artículo de operación de AMR que recomienda umbrales de regreso por encima de 10-15 %, y un hilo de Open-RMF con un factor de seguridad de 1,2 sobre el umbral; ninguna de las dos páginas se pudo abrir y verificar, así que no se usan como fuente.

Propuesta: reserva de **15 %** al llegar a la base. Es el extremo alto de un rango de 10-15 % con respaldo parcial, y coincide con el umbral crítico del simulador (el regreso se propone a 15 %, `CRITICAL_BATTERY_PERCENT`), por lo que el robot llega a la base con ~13-14 %. Con los coeficientes propuestos, esa reserva equivale a ~65 Wh: alcanza para volver desde el punto más lejano (~80 m, 0,8 % más unos segundos de reposo) y para quedar ~35 min parado. **Mantener 10 % también es defendible** con AMR-1; la diferencia no cambia ningún escenario.

### 2.6 Mínimo para salir a la ronda completa

Se **mantiene en 40 %**. Con los coeficientes propuestos la ronda cuesta ~7,4 % y la reserva es 15 %, o sea que físicamente alcanzaría con ~23 %. El 40 % es una regla operativa del grupo: deja ~17 puntos de margen para un consumo real que no se midió (envejecimiento de la batería, baja temperatura, suelos resbalosos, una espera larga por un incidente). Para que la fórmula de `BatteryPolicy.percentToStart` siga dando 40 con 1,0 % por PC y 15 % de reserva, el margen fijo debería pasar de 2 a 17 puntos. Es un ajuste de `core`, que no se toca desde este documento.

## 3. Consecuencias para el simulador

- Con los coeficientes propuestos, una ronda completa gasta ~7 % de batería. En una demo de pocos minutos la batería casi no se mueve; los escenarios que necesitan verla bajar (E2) ya usan `drainFactor` y una carga inicial baja, y es la forma correcta de acelerarlo sin falsear el modelo. La cuenta de la presentación debe decir que el consumo es realista y que la demo lo acelera.
- La carga en base del simulador (0,01 %/s, ~2,8 h de 0 a 100 %) no tiene respaldo: el cargador de la EDU es de 33,6 V y 9 A (GO2-1), unos 300 W, que cargarían 432 Wh en ~1,5-2 h. Propuesta: 0,015 %/s (cálculo propio, sin medición; se omite el pico de potencia y la curva de carga).

## 4. Qué quedó como supuesto

| Supuesto | Por qué |
|---|---|
| Consumo en reposo de 108 W | Ninguna fuente lo publica; se toma el extremo de 4 h |
| Velocidad de 0,8 m/s | Valor del simulador; Unitree no da velocidad típica de ronda |
| Parada de 30 s por punto de control | Decisión de diseño del caso |
| Margen de 15 % sobre el consumo por metro | Criterio del grupo |
| Reserva de 15 % y mínimo de 40 % | Criterio del grupo con respaldo parcial (AMR-1, GO2-8) |
| Autonomía de 2-4 h aplicable a la ronda | La cifra es de Unitree/manual en condiciones específicas; no hay medición independiente |
| Los 432 Wh | Salen de distribuidores (28,8 V nominal x 15 Ah); no los publica Unitree |

Nada de esto se probó con un Go2 real. Medir el consumo de una ronda real (con la telemetría de batería del propio robot) reemplazaría este modelo.
