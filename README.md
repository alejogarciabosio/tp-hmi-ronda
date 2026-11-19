# Ronda: supervisión de un robot de servicio (TP HMI/HRI)

Trabajo práctico de Desarrollo de Aplicaciones I (UADE). Aplicación Android que supervisa y comanda un robot de servicio en una ronda de inspección nocturna. El APK funciona sin robot, sin servidor y sin internet: el robot está simulado dentro de la app.

El contrato JSON de telemetría, comandos y eventos es una propuesta propia del proyecto; no es un protocolo oficial de Unitree.

## Entregables

- Informe: [`docs/entregas/entrega-final.md`](docs/entregas/entrega-final.md) (y su [PDF](docs/entregas/entrega-final.pdf)).
- APK: [`docs/entregas/apk/ronda-debug.apk`](docs/entregas/apk/ronda-debug.apk).
- Prototipo en Figma: link en [`design/FIGMA.md`](design/FIGMA.md).
- Evidencias de los cinco escenarios (capturas, transcripciones y video): [`docs/evidencias/`](docs/evidencias/README.md).

## Estructura

- `app/`: módulo Android (Compose + Material 3, MVVM, Room, `AppContainer`).
- `core/`: módulo Kotlin/JVM puro con dominio, contrato JSON, `Repository` y simulador. No depende de Android.
- `docs/`: entrega final, contrato, arquitectura, evidencias, decisiones, riesgos y fuentes.
- `design/`: exports y notas del prototipo en Figma.

## Cómo correrlo

Requisitos: JDK 17 o 21 (con JDK 25, Gradle 8.14 no arranca). Para `:app` además el Android SDK (plataforma 35 y build-tools 35.0.0), indicado con `ANDROID_HOME` o `sdk.dir` en `local.properties`.

```bash
./gradlew :core:test          # tests del núcleo (no necesita Android SDK)
./gradlew :app:assembleDebug  # APK en app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest
```

## Stack

Kotlin 2.1, Gradle 8.14 con version catalog (`gradle/libs.versions.toml`), AGP 8.9, Compose BOM 2025.06, Room 2.7 (con KSP), kotlinx.serialization y kotlinx.coroutines. minSdk 26, compileSdk/targetSdk 35, Java 17.
