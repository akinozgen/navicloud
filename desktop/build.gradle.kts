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
    implementation(libs.jmdns) // mDNS keşif (uzaktan kumanda cihaz listesi)
    // Linux D-Bus: StatusNotifierItem tepsisi + MPRIS (Windows'ta yüklenmez, sadece classpath'te durur)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinxCoroutines.get()}")
}

compose.desktop {
    application {
        mainClass = "com.ozgen.navicloud.desktop.MainKt"
        nativeDistributions {
            // Dağıtım her platformda createDistributable (app-image) üzerinden:
            // Windows → NSIS (build-release.ps1), Linux → kendi spec/control'ümüzle
            // rpm+deb (scripts/build-release-linux.sh). jpackage'ın packageRpm/Deb'i
            // kullanılmaz — menü girdisini %post scriptiyle kuruyordu ve dnf'nin
            // SELinux-kısıtlı scriptlet bağlamında sessizce başarısız olabiliyordu.
            // dbus-java SASL auth'u com.sun.security.auth.module.UnixSystem kullanır —
            // jlink runtime'ına eklenmezse SNI tepsisi/MPRIS pakette sessizce devre dışı kalır
            modules("jdk.security.auth")
            packageName = "NaviCloud"
            packageVersion = "1.4.1"
            description = "Navidrome istemcisi"
            vendor = "ozgen"
            windows {
                iconFile.set(project.file("icons/navicloud.ico"))
            }
            linux {
                iconFile.set(project.file("icons/navicloud.png"))
            }
        }
        // libmpv-2.dll dağıtıma gömülür (windows-x64 altı → app/resources)
        nativeDistributions.appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging"))
    }
}
