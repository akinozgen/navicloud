package com.ozgen.navicloud.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.ui.AppContainer
import com.ozgen.navicloud.ui.LocalAppContainer
import com.ozgen.navicloud.ui.NaviCloudRoot
import com.ozgen.navicloud.ui.theme.NaviCloudTheme
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/** NaviCloud Desktop: paylaşılan UI + libmpv ses motoru. */
fun main() = application {
    val container = remember {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val okHttp = OkHttpClient()
        val servers = DesktopServerSource(okHttp, json)
        val music = MusicRepository(servers, InMemoryApiCacheStore(), json, AlwaysOnlineSource())
        AppContainer(
            music = music,
            player = MpvPlayerController(MpvEngine(), music),
            servers = servers,
            downloads = NoDownloads(),
            offline = AlwaysOnlineSource(),
            recents = InMemoryRecentSearches(),
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NaviCloud",
        state = WindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        // Coil3 masaüstü: OkHttp fetcher + disk cache (~/.navicloud/image_cache)
        setSingletonImageLoaderFactory { ctx ->
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .memoryCache { MemoryCache.Builder().maxSizeBytes(128L * 1024 * 1024).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(System.getProperty("user.home"), ".navicloud/image_cache"))
                        .maxSizeBytes(250L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        }
        CompositionLocalProvider(LocalAppContainer provides container) {
            NaviCloudTheme {
                NaviCloudRoot(
                    platformSettings = { DesktopSettingsPlaceholder() },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DesktopSettingsPlaceholder() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Masaüstü ayarları (ses motoru seçimi: libmpv) D5'te geliyor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
