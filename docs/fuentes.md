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
| Base de carga | "Compatible con pila de carga": X y EDU | GO2-1 | Acople autónomo mediante API: no confirmado. |
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
