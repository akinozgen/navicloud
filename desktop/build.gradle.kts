plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}

compose.desktop {
    application {
        mainClass = "com.ozgen.navicloud.desktop.MainKt"
        nativeDistributions {
            packageName = "NaviCloud"
            packageVersion = "0.1.0"
        }
    }
}
