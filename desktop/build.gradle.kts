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
            // Windows dağıtımı NSIS ile createDistributable üzerinden yapılır (build-release.ps1);
            // targetFormats yalnızca Linux packageRpm/packageDeb için gerekli.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "NaviCloud"
            packageVersion = "1.4.0"
            description = "Navidrome istemcisi"
            vendor = "ozgen"
            windows {
                iconFile.set(project.file("icons/navicloud.ico"))
            }
            linux {
                iconFile.set(project.file("icons/navicloud.png"))
                // rpm/deb paket adı küçük harf ister; menü girdisi packageName'den gelir
                packageName = "navicloud"
                debMaintainer = "akin@isskontrol.com.tr"
                menuGroup = "AudioVideo;Audio;Player"
                appCategory = "Audio"
                // Ses motoru sistem libmpv'sini kullanır (Fedora: mpv-libs, Debian/Ubuntu: libmpv2).
                // jpackage harici paket bağımlılığı yazamıyor — README + çalışma anı hatasıyla yönetiliyor.
            }
        }
        // libmpv-2.dll dağıtıma gömülür (windows-x64 altı → app/resources)
        nativeDistributions.appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging"))
    }
}
