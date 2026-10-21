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

        // URL base de la API Laravel en desarrollo.
        // Se usa 127.0.0.1 (el propio celular) junto con el comando:
        //     adb reverse tcp:8000 tcp:8000
        // que redirige el puerto 8000 del celular al puerto 8000 del computador.
        // Por que no 10.0.2.2: desde Android 17 (API 37) las apps tienen bloqueado por
        // defecto el acceso a la "red local" y 10.0.2.2 es una direccion local, asi que
        // haria falta pedir el permiso ACCESS_LOCAL_NETWORK, que en produccion no tiene
        // sentido (alli la API estara en internet con https). adb reverse ademas sirve
        // igual para el emulador y para un celular fisico conectado por USB.
        buildConfigField("String", "API_URL", "\"http://127.0.0.1:8000/api/\"")
    }

    // ---------- Product flavors (sabores del producto) ----------
    // Un "flavor" es una VARIANTE de la misma app que se compila a partir del mismo codigo.
    // Aqui hay dos, dentro de una misma "dimension" llamada "mode":
    //   - full: la app real, que habla con la API Laravel. No cambia nada respecto a antes.
    //   - demo: la app de demostracion, con datos de ejemplo en memoria y sin servidor.
    // Gradle combina flavor + buildType y crea variantes: fullDebug, fullRelease,
    // demoDebug y demoRelease (en Android Studio se eligen en el panel "Build Variants").
    //
    // Cada flavor puede tener su propia carpeta de codigo ("source set"):
    //   app/src/main  -> codigo comun a todos los flavors
    //   app/src/full  -> solo se compila en el flavor full
    //   app/src/demo  -> solo se compila en el flavor demo
    flavorDimensions += "mode"
    productFlavors {
        create("full") {
            dimension = "mode"
            // resValue crea un recurso de texto; el manifiesto lo usa como nombre de la app
            // con android:label="@string/app_name".
            resValue("string", "app_name", "SyncPOS")
            // BuildConfig.DEMO es una constante que el codigo puede consultar: false = app real.
            buildConfigField("boolean", "DEMO", "false")
        }
        create("demo") {
            dimension = "mode"
            // Otro applicationId: Android lo ve como OTRA app, asi que full y demo se
            // pueden instalar al mismo tiempo en el mismo celular.
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "SyncPOS Demo")
            buildConfigField("boolean", "DEMO", "true")
        }
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
        buildConfig = true // necesario para usar BuildConfig.API_URL y BuildConfig.DEMO
        resValues = true   // necesario para que los flavors definan @string/app_name
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

    // Escaner de codigos de barras de Google (no necesita permiso de camara)
    implementation(libs.play.services.code.scanner)

    // Pruebas unitarias (JVM, sin emulador)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
