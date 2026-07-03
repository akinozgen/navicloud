package com.ozgen.navicloud.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.ui.LocalAppContainer
import com.ozgen.navicloud.ui.screens.login.LoginScreen
import com.ozgen.navicloud.ui.screens.settings.LicensesScreen
import com.ozgen.navicloud.ui.screens.settings.commonLicenses
import com.ozgen.navicloud.ui.screens.settings.desktopLicenses
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Masaüstüne özgü ayarlar. Ses motoru seçimi ileriye dönük — şimdilik tek
 * seçenek libmpv (varsayılan); GStreamer vb. eklendiğinde buradan seçilecek.
 */
enum class AudioBackend(val label: String, val description: String) {
    LIBMPV("libmpv", "Varsayılan"),
}

object DesktopPrefs {
    @kotlinx.serialization.Serializable
    private data class Prefs(
        val audioBackend: String = AudioBackend.LIBMPV.name,
        val streamQuality: String = StreamQuality.RAW.name,
        val offlineMode: Boolean = false,
        val volume: Int = 100,
        val closeToTray: Boolean = true,
    )

    private val file = File(System.getProperty("user.home"), ".navicloud/settings.json")
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): Prefs =
        runCatching { json.decodeFromString<Prefs>(file.readText()) }.getOrDefault(Prefs())

    private fun save(p: Prefs) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Prefs.serializer(), p))
        }
    }

    /** Player canlı okusun diye flow — ayar değişince sonraki parça yeni kaliteyle akar. */
    val streamQualityFlow: MutableStateFlow<StreamQuality> = MutableStateFlow(
        runCatching { StreamQuality.valueOf(load().streamQuality) }.getOrDefault(StreamQuality.RAW)
    )

    var audioBackend: AudioBackend
        get() = runCatching { AudioBackend.valueOf(load().audioBackend) }.getOrDefault(AudioBackend.LIBMPV)
        set(value) = save(load().copy(audioBackend = value.name))

    var streamQuality: StreamQuality
        get() = streamQualityFlow.value
        set(value) {
            streamQualityFlow.value = value
            save(load().copy(streamQuality = value.name))
        }

    val offlineModeFlow: MutableStateFlow<Boolean> = MutableStateFlow(load().offlineMode)

    var offlineMode: Boolean
        get() = offlineModeFlow.value
        set(value) {
            offlineModeFlow.value = value
            save(load().copy(offlineMode = value))
        }

    var volume: Int
        get() = load().volume
        set(value) = save(load().copy(volume = value.coerceIn(0, 100)))

    var closeToTray: Boolean
        get() = load().closeToTray
        set(value) = save(load().copy(closeToTray = value))
}

@Composable
fun DesktopSettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val servers by container.servers.servers.collectAsState(emptyList())
    val active by container.servers.activeServer.collectAsState(null)
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var backendDialog by remember { mutableStateOf(false) }
    var backend by remember { mutableStateOf(DesktopPrefs.audioBackend) }
    var quality by remember { mutableStateOf(DesktopPrefs.streamQuality) }
    var qualityDialog by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }

    if (adding) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { adding = false }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
                }
                Text("Sunucu ekle", style = MaterialTheme.typography.titleMedium)
            }
            LoginScreen()
        }
        return
    }

    if (showLicenses) {
        LicensesScreen(entries = commonLicenses + desktopLicenses, onBack = { showLicenses = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .widthIn(max = 720.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
            }
            Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        }

        SectionHeader("Sunucular") {
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Sunucu ekle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        servers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { container.servers.setActive(server.id) } }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = server.id == active?.id,
                    onClick = { scope.launch { container.servers.setActive(server.id) } },
                )
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${server.username} @ ${server.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (servers.size > 1) {
                    IconButton(onClick = { scope.launch { container.servers.removeServer(server.id) } }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionHeader("Çalma")
        SettingRow(
            icon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Akış kalitesi",
            subtitle = quality.label,
            onClick = { qualityDialog = true },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Ses motoru",
            subtitle = backend.label,
            onClick = { backendDialog = true },
        )
        var offline by remember { mutableStateOf(DesktopPrefs.offlineMode) }
        SettingRow(
            icon = { Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Offline mod",
            subtitle = "Yalnızca indirilenlerden çalar, ağı kullanmaz",
            onClick = {
                offline = !offline
                DesktopPrefs.offlineMode = offline
            },
            trailing = {
                Switch(checked = offline, onCheckedChange = {
                    offline = it
                    DesktopPrefs.offlineMode = it
                })
            },
        )

        SectionHeader("İndirmeler")
        val dlCount by container.downloads.totalCount.collectAsState(0)
        val dlBytes by container.downloads.totalSizeBytes.collectAsState(0L)
        val activeDl by container.downloads.active.collectAsState()
        SettingRow(
            icon = { Icon(Icons.Rounded.DownloadForOffline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "$dlCount şarkı",
            subtitle = formatBytes(dlBytes),
            onClick = {},
            trailing = {
                if (dlCount > 0) {
                    TextButton(onClick = { scope.launch { container.downloads.clearAll() } }) {
                        Text("Tümünü sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
        activeDl?.let { dl ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "İndiriliyor: ${dl.title}" + if (dl.queued > 1) " (+${dl.queued - 1} sırada)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { dl.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }

        SectionHeader("Önbellek")
        var imageCacheBytes by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            imageCacheBytes = runCatching {
                coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache?.size ?: 0L
            }.getOrDefault(0L)
        }
        SettingRow(
            icon = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Görsel önbelleği",
            subtitle = formatBytes(imageCacheBytes) + " • kapak görselleri",
            onClick = {},
            trailing = {
                if (imageCacheBytes > 0) {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                val loader = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE)
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                            }
                            imageCacheBytes = 0L
                        }
                    }) { Text("Temizle") }
                }
            },
        )

        SectionHeader("Kütüphane")
        val scanState = scanning
        SettingRow(
            icon = { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Kütüphaneyi tara",
            subtitle = when {
                scanState?.first == true -> "Taranıyor… ${scanState.second} öğe"
                scanState != null -> "Tamamlandı • ${scanState.second} öğe"
                else -> "Sunucuda yeni dosyaları bulur"
            },
            onClick = {
                scope.launch {
                    runCatching { container.music.startScan(false) }.onSuccess { scanning = it }
                    while (scanning?.first == true) {
                        delay(1500)
                        runCatching { container.music.scanStatus() }.onSuccess { scanning = it }
                    }
                }
            },
            trailing = {
                if (scanState?.first == true) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
        )

        SectionHeader("Uygulama")
        var closeToTray by remember { mutableStateOf(DesktopPrefs.closeToTray) }
        SettingRow(
            icon = { Icon(Icons.Rounded.Minimize, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Kapatınca tepsiye küçült",
            subtitle = "Pencereyi kapatınca uygulama tepside çalmaya devam eder",
            onClick = {
                closeToTray = !closeToTray
                DesktopPrefs.closeToTray = closeToTray
            },
            trailing = {
                Switch(checked = closeToTray, onCheckedChange = {
                    closeToTray = it
                    DesktopPrefs.closeToTray = it
                })
            },
        )

        SectionHeader("Hakkında")
        SettingRow(
            icon = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Açık kaynak lisansları",
            subtitle = "Kullanılan kütüphaneler ve lisansları (libmpv dâhil)",
            onClick = { showLicenses = true },
        )
        Spacer(Modifier.height(32.dp))
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Akış kalitesi") },
            text = {
                Column {
                    StreamQuality.entries.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    quality = q
                                    DesktopPrefs.streamQuality = q
                                    qualityDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = quality == q, onClick = {
                                quality = q
                                DesktopPrefs.streamQuality = q
                                qualityDialog = false
                            })
                            Text(q.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { qualityDialog = false }) { Text("Kapat") }
            },
        )
    }

    if (backendDialog) {
        AlertDialog(
            onDismissRequest = { backendDialog = false },
            title = { Text("Ses motoru") },
            text = {
                Column {
                    AudioBackend.entries.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    backend = b
                                    DesktopPrefs.audioBackend = b
                                    backendDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = backend == b, onClick = {
                                backend = b
                                DesktopPrefs.audioBackend = b
                                backendDialog = false
                            })
                            Column {
                                Text(b.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    b.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { backendDialog = false }) { Text("Kapat") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}
