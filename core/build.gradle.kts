plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // El JDK local puede ser más nuevo; se compila con release 17 para coincidir con :app.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Regenera docs/evidencias/transcripciones/ corriendo los cinco escenarios con tiempo virtual.
tasks.register<Test>("scenarioTranscripts") {
    group = "verification"
    description = "Escribe las transcripciones de los escenarios en docs/evidencias/transcripciones"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*ScenarioTranscriptsTest") }
    systemProperty("ronda.transcripts.dir", rootProject.file("docs/evidencias/transcripciones").absolutePath)
    outputs.upToDateWhen { false }
}
