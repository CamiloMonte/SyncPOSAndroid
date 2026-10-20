// Build raiz: solo declara los plugins (con "apply false" no se activan aqui,
// se activan en el modulo app). Las versiones viven en gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
