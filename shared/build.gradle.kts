// Platform-bağımsız çekirdek: model + Subsonic istemcisi + repository'ler.
// Saf Kotlin/JVM — hem Android app hem Compose Desktop buna bağlanır.
// Android API'si ve Compose bu modüle GİREMEZ.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    // @Inject/@Singleton — Hilt (Android) ve Koin/manuel (desktop) ikisi de okuyabilir
    api(libs.javax.inject)
}
