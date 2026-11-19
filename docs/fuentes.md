# Fuentes técnicas

Todas las páginas se consultaron el **2026-11-19**; el contenido del Document Center (GO2-5) no pudo leerse (ver más abajo). Las fichas de distribuidores se usan solo como contraste de la ficha oficial; cuando difieren, se indica. Los precios son de lista, sin impuestos ni flete, y cambian: sirven para ordenar magnitudes, no para presupuestar.

## Unitree Go2

| ID | Tipo | Fuente | URL | Consulta |
|---|---|---|---|---|
| GO2-1 | Oficial | Unitree, página de producto Go2 (tabla Air / Pro / X / EDU) | https://www.unitree.com/go2 | 2026-11-19 |
| GO2-2 | Desarrollador | Repositorio `unitree_sdk2` (README: SDK en C++, BSD-3-Clause, Ubuntu 20.04, remite al Document Center) | https://github.com/unitreerobotics/unitree_sdk2 | 2026-11-19 |
| GO2-3 | Desarrollador | Repositorio `unitree_sdk2_python` (README: interfaz de alto y bajo nivel, cámara frontal, interruptor de evasión de obstáculos, control de luz y volumen, DDS con CycloneDDS 0.10.2) | https://github.com/unitreerobotics/unitree_sdk2_python | 2026-11-19 |
| GO2-4 | Desarrollador | Repositorio `unitree_ros2` (README: soporte ROS 2 sobre DDS; Foxy con Ubuntu 20.04, Humble con Ubuntu 22.04 recomendado) | https://github.com/unitreerobotics/unitree_ros2 | 2026-11-19 |
| GO2-5 | Desarrollador | Unitree Document Center (developer), referenciado desde los README | https://support.unitree.com/home/en/developer | 2026-11-19 |
| GO2-6 | Ficha de distribuidor | Robostore, Go2 EDU (40 TOPS) | https://robostore.com/products/go2-edu-40-tops-computing-quadruped-robot-dog | 2026-11-19 |
| GO2-7 | Ficha de distribuidor | GlobalDroneHQ, Go2 EDU | https://globaldronehq.com/products/unitree-go2-edu-quadruped-robot | 2026-11-19 |
| GO2-8 | Manual | Manual de usuario Go2 (ManualsLib): restricciones de uso, temperatura, agua, evasión, escaleras | https://www.manualslib.com/manual/3774520/Unitree-Go2.html | 2026-11-19 |

Limitación sobre GO2-5: el Document Center es una aplicación que se renderiza en el navegador; al abrirlo con la herramienta de lectura devolvió solo el título, sin contenido. Las páginas de servicios (`sports_services`, `ObstaclesAvoidClient`, `VuiClient`, `Basic_services`, `Quick_start`) se conocen por los enlaces del README de GO2-3, pero **su contenido no se pudo leer**. Lo que se afirma sobre el SDK sale de los README de GitHub (GO2-2 a GO2-4).

## Unitree G1

| ID | Tipo | Fuente | URL | Consulta |
|---|---|---|---|---|
| G1-1 | Oficial | Unitree, página de producto G1 (tabla G1 / G1 EDU) | https://www.unitree.com/g1 | 2026-11-19 |
| G1-2 | Ficha de distribuidor | RobotShop, Unitree G1 (SKU RB-Unt-51) | https://www.robotshop.com/products/unitree-g1-humanoid-robot-us | 2026-11-19 |

## Datos extraídos y discrepancias entre fuentes

### Go2

| Dato | Valor | Fuente | Observación |
|---|---|---|---|
| Dimensiones de pie | 70 x 31 x 40 cm | GO2-1 | |
| Peso | ~15 kg con batería | GO2-1 | |
| Actuadores | 12 motores articulares de aleación de aluminio; par pico ~45 N·m | GO2-1 | La ficha no indica "grados de libertad" de forma explícita; se cita la cantidad de motores. |
| Batería | 8000 mAh (Air/Pro/X); **15000 mAh de larga duración (EDU)** | GO2-1 | GO2-6 también muestra 8000 mAh en un campo de su ficha; GO2-1, GO2-6 y GO2-7 coinciden en 15000 mAh para EDU. Se adopta 15000 mAh. |
| Autonomía | ~1-2 h (8000 mAh); **~2-4 h (15000 mAh)** | GO2-1, GO2-6, GO2-7 | GO2-8: baja con velocidad alta, carga, cambios de postura bruscos y terreno irregular. Para planificar se usa el extremo bajo. |
| Cargador | 33,6 V 9 A, carga rápida (EDU) | GO2-1, GO2-6, GO2-7 | |
| Velocidad | 0-3,7 m/s (EDU), máx. ~5 m/s en laboratorio | GO2-1, GO2-7 | GO2-6 consigna "2 m/s" en su tabla y "11 mph" en otro apartado: se descarta por inconsistente. |
| Carga útil | ~8 kg (máx. ~12 kg en EDU) | GO2-1, GO2-7 | GO2-6 da 10 kg y 12 kg en distintos lugares. Irrelevante para el caso (solo lleva un sensor liviano). |
| Escalón / pendiente | Escalón máx. ~16 cm; pendiente máx. 40° | GO2-1, GO2-6, GO2-7, GO2-8 | GO2-8: "no se recomienda" subir escalones de más de 16 cm. GO2-1 no menciona "escaleras" como tal. |
| LiDAR | 4D LiDAR L2, 360 x 96°, distancia mínima 0,05 m | GO2-1, GO2-6, GO2-7 | Fichas antiguas de otros comercios hablan del L1 (360 x 90°); se adopta la ficha oficial (L2). |
| Cámara | Gran angular HD frontal en todas las versiones (GO2-6: 1280x720, 120°) | GO2-1, GO2-6 | |
| Cámara de profundidad | "Solo EDU" | GO2-1 | **Difiere**: GO2-6 la lista como accesorio compatible (Intel RealSense D435i, no incluido) y GO2-7 la ubica en configuraciones Plus o superiores. No es necesaria para el caso. |
| Conectividad | Wi-Fi 6 doble banda, Bluetooth, 4G con GPS (Pro/X/EDU) | GO2-1 | |
| Cómputo | CPU de 8 núcleos; EDU admite módulos externos tipo Orin (40-100 TOPS) | GO2-1 | |
| Desarrollo secundario | Ninguno en Air/Pro; parcial en X; **completo en EDU** | GO2-1 | |
| SDK | `unitree_sdk2` (C++, BSD-3-Clause), `unitree_sdk2_python`, `unitree_ros2`; comunicación por DDS (CycloneDDS) | GO2-2, GO2-3, GO2-4 | El README de `unitree_ros2` declara soporte para "Go2, B2 y H1". |
| Voz / parlante | "Funciones de voz"; parlante de 3 W | GO2-1 (voz), GO2-6 (3 W) | GO2-8 no describe parlante. El SDK incluye control de volumen y luz (`vui_client`, GO2-3). La potencia del parlante la da un solo distribuidor. |
| Evasión de obstáculos | Evasión inteligente, **solo hacia adelante** | GO2-6, GO2-8 | GO2-3 incluye un ejemplo con interruptor del servicio de evasión. |
| Mapeo | "Mapeo 3D con LiDAR" como función de software | GO2-1 | Mapa persistente por puntos de control y navegación entre pisos: no documentado en las fuentes leídas. |
| Base de carga | "Compatible con pila de carga": X y EDU | GO2-1 | El manual (GO2-8) agrega que la EDU soporta carga en pila y puede planificar la ruta de carga; ver la ampliación del 2026-11-19. Acople autónomo mediante API del SDK: no confirmado. |
| Temperatura de operación | 5 a 35 °C | GO2-7, GO2-8 | GO2-1 no la lista. |
| Agua / polvo | No es resistente al agua ni al polvo; sin grado IP | GO2-7, GO2-8 | GO2-1 no lista IP. |
| Restricciones de uso | Mantener 2 m de obstáculos y multitudes; velocidad reducida en suelos de baja fricción (hielo, baldosa lisa); no enviar comandos de alto nivel y de control remoto a la vez | GO2-8 | |
| Precio EDU | USD ~11.190-11.200 (configuración estándar) | GO2-6, GO2-7 | GO2-1: "consultar a ventas". |

### G1

| Dato | Valor | Fuente | Observación |
|---|---|---|---|
| Dimensiones | 1320 x 450 x 200 mm de pie; 690 x 450 x 300 mm plegado | G1-1 | |
| Peso | ~35 kg con batería (EDU: 35 kg o más) | G1-1, G1-2 | |
| Grados de libertad | 23 (EDU: 23 a 43) | G1-1, G1-2 | |
| Batería | 9000 mAh, extraíble | G1-1, G1-2 | |
| Autonomía | ~2 h | G1-1, G1-2 | |
| Sensores | Cámara de profundidad + LiDAR 3D, arreglo de 4 micrófonos; parlante 5 W | G1-1 | G1-2 coincide en sensores. |
| Carga en brazos | ~2 kg (EDU ~3 kg) | G1-1, G1-2 | |
| Velocidad de marcha | **No figura** en las fuentes leídas | G1-1, G1-2 | No se afirma ningún valor. |
| Desarrollo secundario | Solo EDU | G1-1 | G1-2: la versión base no menciona SDK. |
| Precio | G1 base USD 13,5 mil (oficial, sin impuestos ni flete); USD 17.900 en RobotShop | G1-1, G1-2 | Difieren: el distribuidor vende por pedido especial e incluye su margen. |

## Ampliación del 2026-11-19 (capacidades, batería y antecedentes)

Consulta de todas las páginas: **2026-11-19**. GO2-1 y GO2-8 se volvieron a leer con otras preguntas; lo nuevo se anota abajo.

### Fuentes nuevas

| ID | Tipo | Fuente | URL | Consulta |
|---|---|---|---|---|
| GO2-9 | Ficha de distribuidor | OpenElab, batería Go2 BT2-06 de larga duración (15000 mAh / 432 Wh, 28,8 V nominal, 33,6 V de carga, 3,5-9 A) | https://openelab.io/pl/products/unitree-go2-battery-long-batterylife | 2026-11-19 |
| GO2-10 | Ficha de distribuidor | Robostore, batería Go2 ("8000 mAh, 236,8 Wh (estándar), 15000 mAh 432 Wh (extendida)"; tope de carga 33,6 V) | https://robostore.com/products/unitree-go2-ai-robot-dog-battery | 2026-11-19 |
| GO2-11 | Ficha de distribuidor | QuakeLogic, datasheet Go2 EDU+ (rango de tensión 28-33,6 V, potencia máxima 3000 W, autonomía 2-4 h, escalón 16 cm, pendiente 40 °) | https://quakelogic.net/datasheets/QL-Unitree-Go2-EDU+.pdf | 2026-11-19 |
| GO2-12 | Proyecto comunitario | `Kodo-Robotics/go2-autonomous-patrol` (ROS 2, Nav2, YOLO, acople por AprilTag sobre un Go2 EDU) | https://github.com/Kodo-Robotics/go2-autonomous-patrol | 2026-11-19 |
| GO2-13 | Prensa | Shanghai Daily / City News Service, 28-09-2025: patrullaje urbano con tres Unitree B1 y tres Go2 | https://www.citynewsservice.cn/articles/shanghaidaily/news/shanghai-deploys-robot-dogs-drones-for-city-patrols-dmjb0gen | 2026-11-19 |
| GO2-14 | Guía de distribuidor | GlobalDroneHQ, guía de cuadrúpedos para patrullaje de seguridad | https://globaldronehq.com/blogs/guides/quadruped-robots-security-patrol | 2026-11-19 |
| SPOT-1 | Oficial | Boston Dynamics, página de producto Spot (564 Wh, ~90 min, reposo ~180 min, carga ~60 min, 1,6 m/s, escalón 300 mm, -20 a 55 °C, "autonomously charging") | https://bostondynamics.com/products/spot/ | 2026-11-19 |
| ANY-1 | Oficial | ANYbotics, página de ANYmal (90 min, docking station, 2 km por carga, 0,75 m/s, IP67) | https://www.anybotics.com/anymal | 2026-11-19 |
| AMR-1 | Configuración de referencia | Open-RMF `rmf_demos`, `tinyRobot_config.yaml` (`recharge_threshold: 0.10`) | https://raw.githubusercontent.com/open-rmf/rmf_demos/main/rmf_demos/config/office/tinyRobot_config.yaml | 2026-11-19 |
| AMR-2 | Artículo técnico | Automated Warehouse, "Power Management for Autonomous Mobile Robots" (no da porcentajes; solo dice que el robot vigila la carga y decide cuándo ir al cargador) | https://www.automatedwarehouseonline.com/power-management-for-autonomous-mobile-robots/ | 2026-11-19 |

### Datos nuevos del Go2

| Dato | Valor | Fuente | Observación |
|---|---|---|---|
| Energía de la batería EDU | 15000 mAh a 28,8 V nominal = **432 Wh** (cuenta: 15 Ah x 28,8 V) | GO2-9, GO2-10 | Unitree (GO2-1) publica solo mAh y un rango de 28-33,6 V; los 432 Wh salen de dos distribuidores. La de 8000 mAh figura como 236,8 Wh (GO2-10), coherente con 28,8 V. GO2-11 lista 8000 y 15000 mAh a la vez para la EDU+. |
| Potencia máxima | ~3000 W | GO2-1, GO2-11 | Es un pico de trabajo; no sirve para estimar autonomía. |
| Condición de la autonomía | "2-4 h" medida "sin carga, de pie y caminando de forma alternada"; baja con marcha rápida, cuerpo bajo y carga | GO2-8 | No hay distancia por carga ni consumo en reposo publicados. No se encontró ninguna medición independiente del Go2 EDU. |
| Batería baja | Luz amarilla: "se agachará automáticamente en 10 minutos"; con una sola barra el manual pide detener y apagar el robot | GO2-8 | |
| Detección de personas | **No figura** como función nativa del Go2. GO2-1 lista "seguimiento de persona (ISS 2.0)" (opcional en Air, incluido en Pro/X/EDU) junto con "posicionamiento inalámbrico"; el manual (GO2-8) habla de la altura del control remoto respecto de la cabeza del robot | GO2-1, GO2-8 | Se interpreta que ISS 2.0 es un seguimiento apoyado en el control remoto, no un detector visual (interpretación propia). El proyecto GO2-12 detecta personas con un YOLO propio sobre la cámara frontal, lo que indica que hay que desarrollarlo. |
| Evasión de obstáculos | LiDAR 360 x 96 ° para percepción; el manual dice que la evasión **solo soporta obstáculos hacia adelante** | GO2-1, GO2-8 | Confirma GO2-8. |
| Carga autónoma | La EDU "soporta carga en pila de carga" y "puede planificar la ruta de carga y realizar carga autónoma" | GO2-8 (manual), GO2-1 (compatibilidad X y EDU) | El texto del manual se leyó mediante la herramienta de lectura, sin ver la página original. El servicio equivalente en el SDK no se pudo leer (GO2-5 no se renderiza). El proyecto GO2-12 resuelve el acople con Nav2 y AprilTag, es decir, por fuera del firmware. |
| Escalones y escaleras | Escalón máx. ~16 cm (Air ~15 cm); el manual no recomienda subir escaleras de más de 16 cm | GO2-1, GO2-8 | **Ángulo de escalera: no figura en ninguna fuente.** Lo publicado es pendiente máxima: 40 ° (Pro/X/EDU), 30 ° (Air). |
| Temperatura | 5 a 35 °C | GO2-8 | Sin grado IP. |

### Antecedentes de patrullaje con cuadrúpedos

| Producto | Qué hace | Autonomía y carga | Fuente |
|---|---|---|---|
| Spot (Boston Dynamics) | Rutas grabadas y misiones autónomas; carga sola en dock | 564 Wh; ~90 min (reposo ~180 min); 1,6 m/s; -20 a 55 °C | SPOT-1 |
| ANYmal (ANYbotics) | Inspección industrial; va al dock con batería baja | 90 min; 2 km por carga; 0,75 m/s; IP67 | ANY-1 |
| Go2 con ROS 2 (proyecto comunitario) | Rutas por waypoints, pausa y reanudación, geocercas, voz por parlante, detección de personas con YOLO, acople por AprilTag | Sin datos de batería; hoja de ruta sin completar, no probado en robot real según el README | GO2-12 |
| Go2 y B1 en Shanghai | Patrullaje de calles y obras con cámaras infrarroja y ojo de pez, alertas a un centro de control | Sin datos de batería | GO2-13 |

La guía GO2-14 (de un distribuidor, no neutral) coincide con el planteo del caso: el robot cubre la ronda y deja registro, pero "respuesta, criterio e intervención siguen siendo humanos"; advierte que el límite de un patrullaje suele ser la recarga y que la autonomía publicada es "sin carga y sobre terreno plano". No se halló una instalación documentada de un Go2 para ronda nocturna en un edificio de oficinas o educativo; los antecedentes son industriales o urbanos.

### Búsquedas sin resultado

- Página del Document Center sobre carga automática, servicio de evasión y VUI: no legible (GO2-5).
- Resolución exacta del ángulo de escalera: ninguna fuente.
- Consumo en reposo del Go2 (W): ninguna fuente.
- Práctica de reserva de batería en AMR: no hay una norma. Ver `docs/supuestos-simulacion.md`.
- Un hilo de Open-RMF sobre un factor de seguridad de 1,2 para volver al cargador apareció en una búsqueda, pero la página dio 403 y no se pudo abrir; no se usa.

## Ampliación del 2026-11-19 (cámara, cómputo a bordo y detección de personas)

Consulta de todas las páginas: **2026-11-19**.

### Fuentes nuevas

| ID | Tipo | Fuente | URL | Consulta |
|---|---|---|---|---|
| CAM-1 | Desarrollador | `unitree_sdk2`, carpeta `example/go2` (incluye `go2_video_client.cpp`); se leyó el código del ejemplo | https://github.com/unitreerobotics/unitree_sdk2/blob/main/example/go2/go2_video_client.cpp | 2026-11-19 |
| CAM-2 | Desarrollador | `unitree_sdk2_python`, carpeta `example/go2/front_camera` (`capture_image.py`, `camera_opencv.py`); se leyó el código de ambos | https://github.com/unitreerobotics/unitree_sdk2_python/tree/master/example/go2/front_camera | 2026-11-19 |
| GO2-15 | Ficha de distribuidor | Robostore, Go2 EDU Plus 100 TOPS ("NVIDIA Jetson Orin NX 16gb, 100 TOPS") | https://robostore.com/products/go2-edu-plus-100-tops-computing-quadruped-robot-dog | 2026-11-19 |
| JET-1 | Documentación de fabricante de software | Ultralytics, guía NVIDIA Jetson (tablas de benchmark) | https://docs.ultralytics.com/guides/nvidia-jetson/ | 2026-11-19 |
| JET-2 | Proyecto comunitario | `hokwangchoi/jetson-orin-nano-benchmarks` (YOLOv8n en Orin Nano, JetPack 6.2.2, modo MAXN_SUPER) | https://github.com/hokwangchoi/jetson-orin-nano-benchmarks | 2026-11-19 |
| YOL-1 | Documentación de fabricante de software | Ultralytics, YOLOv8 (mAP 37,3 para YOLOv8n a 640 px; 3,2 M de parámetros; preentrenado en COCO) | https://docs.ultralytics.com/models/yolov8/ | 2026-11-19 |
| YOL-2 | Documentación de fabricante de software | Ultralytics, dataset COCO (80 categorías; clase 0 = `person`) | https://docs.ultralytics.com/datasets/detect/coco/ | 2026-11-19 |
| HIL-1 | Artículo técnico de un proveedor | N. Sapunov, "Tuning Analytics: False Alarms, Accuracy, and the Operator's Reality", Fora Soft, 9-06-2026 | https://www.forasoft.com/learn/video-surveillance/articles-vms/tuning-analytics-false-alarms-accuracy | 2026-11-19 |

### Datos extraídos

| Tema | Dato | Fuente | Observación |
|---|---|---|---|
| Cómputo a bordo | La ficha oficial lista el "módulo de alta potencia de cómputo" de la EDU como "varias marcas y modelos, como Orin (40-100 TOPS)"; no nombra Orin Nano ni Orin NX | GO2-1 | **No confirmado por Unitree qué módulo trae cada configuración.** |
| Cómputo a bordo | "NVIDIA Jetson Orin Nano 8gb, 40 TOPS" (EDU de 40 TOPS) y "NVIDIA Jetson Orin NX 16gb, 100 TOPS" (EDU Plus de 100 TOPS) | GO2-6, GO2-15 | Solo distribuidor. Ninguna de las dos páginas dice dónde se monta el módulo. Una búsqueda mostró otros comercios que llaman "Orin NX 8GB" al de 40 TOPS y describen un "docking station" aparte para el módulo; no se abrieron esas páginas y no se usan. El grupo toma como supuesto una configuración de 40 TOPS (Orin Nano 8 GB), la menos favorable. |
| Cámara por SDK | C++: `unitree::robot::go2::VideoClient`, `Init()` y `GetImageSample(std::vector<uint8_t>&)`; devuelve 0 si salió bien. El ejemplo escribe los bytes tal cual en un archivo `.jpg`, cada 3 s | CAM-1 | El código no declara el formato; la extensión `.jpg` indica JPEG (inferencia). |
| Cámara por SDK | Python: `unitree_sdk2py.go2.video.video_client.VideoClient`, `GetImageSample()` devuelve `(code, data)`. `capture_image.py` guarda los bytes en `img.jpg`; `camera_opencv.py` los decodifica con `cv2.imdecode` | CAM-2 | Mismo comentario sobre el formato. Que `cv2.imdecode` los acepte es coherente con JPEG. La resolución de la foto no figura en el ejemplo (la ficha GO2-6 da 1280x720 para la cámara). |
| Cámara por SDK | El Document Center no pudo leerse (GO2-5): no se confirmaron tasa de cuadros, resolución ni latencia del servicio de video | GO2-5 | |
| Rendimiento de YOLO en Jetson | YOLOv8n, 640x640, batch 1, Orin Nano en modo MAXN_SUPER, JetPack 6.2.2: PyTorch FP32 20,9 ms (47,9 FPS); TensorRT FP16 4,43 ms (225 FPS); TensorRT INT8 3,49 ms | JET-2 | Latencia de GPU, sin transferencias H2D/D2H; no aclara si incluye pre y posproceso. Es un solo proyecto comunitario. |
| Rendimiento de YOLO en Jetson | YOLO26n (no v8n), TensorRT, 640 px: 4,57 ms en Orin Nano Super Developer Kit (JetPack 6.1) y 4,13 ms en Orin NX 16 GB (JetPack 6.0 o 5.1.3) | JET-1 | Excluye pre y posproceso. La tabla dice FP32 en el texto y FP16 en la fila: precisión poco clara. No es YOLOv8n. |
| Detector preentrenado | YOLOv8n: mAP 37,3 (COCO val, 640 px), 3,2 M de parámetros; COCO tiene 80 clases y la clase 0 es `person` | YOL-1, YOL-2 | |
| Falsos positivos | Un falso positivo es una alarma por algo que no ocurrió ("persona" disparada por una sombra, un gato o el barrido de un faro); un detector de "99 %" puede equivocarse casi en cada alarma si el evento es raro; el análisis de eventos raros debe ser "triaje más verificación, nunca una alarma autónoma" y su salida "una pista, no una decisión". Los operadores "pierden hasta 45 % de la actividad en pantalla tras ~12 min" | HIL-1 | Fuente de un proveedor de videovigilancia (no una norma ni un paper revisado); es opinión de práctica profesional. No se encontró una norma que exija confirmación humana. |

### Qué no se confirmó

- Modelo y TOPS del módulo de cómputo por configuración, según Unitree (solo rango 40-100 TOPS y fichas de distribuidores).
- FPS y resolución reales del servicio de video del Go2, y el formato de la imagen declarado por Unitree.
- Rendimiento de YOLOv8n en el módulo concreto del Go2 (los benchmarks son de kits de desarrollo y de la placa Super, que puede ser más rápida que la que viene en el robot).
- Una norma, guía oficial o paper revisado sobre confirmación humana de detecciones en seguridad.
