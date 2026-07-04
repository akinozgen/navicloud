plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.jetbrains.navigation.compose)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinxCoroutines.get()}")
}

compose.desktop {
    application {
        mainClass = "com.ozgen.navicloud.desktop.MainKt"
        nativeDistributions {
            packageName = "NaviCloud"
            packageVersion = "1.1.0"
            description = "Navidrome istemcisi"
            vendor = "ozgen"
            windows {
                iconFile.set(project.file("icons/navicloud.ico"))
            }
        }
        // libmpv-2.dll dağıtıma gömülür (windows-x64 altı → app/resources)
        nativeDistributions.appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging"))
    }
}
