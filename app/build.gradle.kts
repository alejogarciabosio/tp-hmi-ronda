plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ar.edu.uade.ronda"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.edu.uade.ronda"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// El video de demostración se graba con un test de Robolectric que no corre en testDebugUnitTest:
// solo `./gradlew :app:recordDemo` lo activa (y entonces corre solo ese test).
val recordingDemo = gradle.startParameter.taskNames.any { it.endsWith("recordDemo") }
val demoFramesDir = layout.buildDirectory.dir("demo-frames").get().asFile

tasks.withType<Test>().configureEach {
    if (recordingDemo) {
        filter { includeTestsMatching("*SupervisionDemoRecorder*") }
        systemProperty("ronda.demo.frames", demoFramesDir.absolutePath)
        outputs.upToDateWhen { false }
    } else {
        filter { excludeTestsMatching("*SupervisionDemoRecorder*") }
    }
}

tasks.register("recordDemo") {
    group = "verification"
    description = "Graba la pantalla Supervisión contra el simulador y arma docs/evidencias/video/supervision-demo.mp4"
    dependsOn("testDebugUnitTest")
    doLast {
        val out = rootProject.file("docs/evidencias")
        exec { commandLine("bash", rootProject.file("docs/evidencias/armar-demo.sh").absolutePath, demoFramesDir.absolutePath, out.absolutePath) }
    }
}
