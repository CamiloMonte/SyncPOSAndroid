// Archivo de ajustes de Gradle: dice de donde se descargan los plugins y las librerias
// y que modulos tiene el proyecto (aqui solo ":app").
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SyncPOS"
include(":app")
