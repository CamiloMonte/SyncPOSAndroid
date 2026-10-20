// Build del modulo app. Aqui se define el SDK, las librerias y las opciones de compilacion.
// Nota: AGP 9 ya trae soporte de Kotlin incorporado, por eso NO se aplica el plugin
// "org.jetbrains.kotlin.android".
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)        // compilador de Jetpack Compose
    alias(libs.plugins.kotlin.serialization)  // genera los convertidores JSON
    alias(libs.plugins.ksp)                   // procesador de anotaciones (lo usa Room)
}

android {
    namespace = "co.sena.syncpos"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.sena.syncpos"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // URL base de la API Laravel. 10.0.2.2 es la direccion con la que el emulador
        // de Android "ve" el localhost del computador donde corre el servidor.
        buildConfigField("String", "API_URL", "\"http://10.0.2.2:8000/api/\"")
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

    buildFeatures {
        compose = true
        buildConfig = true // necesario para usar BuildConfig.API_URL
    }
}

dependencies {
    // Compose: el BOM fija las versiones compatibles de todas las librerias de Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Room (base de datos local). "ksp" genera el codigo de los DAO en compilacion.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Red: Retrofit + OkHttp + JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // permite usar .await() con tareas de Google

    // Escaner de codigos de barras de Google (no necesita permiso de camara)
    implementation(libs.play.services.code.scanner)

    // Pruebas unitarias (JVM, sin emulador)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
