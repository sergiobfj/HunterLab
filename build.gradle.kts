// Todos os plugins declarados aqui (apply false) para que AGP e KGP compartilhem
// o mesmo classloader e a mesma versão do Kotlin em todos os módulos.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
