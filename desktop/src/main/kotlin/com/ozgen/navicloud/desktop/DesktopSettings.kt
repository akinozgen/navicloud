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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.platform.LocalUriHandler
import com.ozgen.navicloud.AppInfo
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Wifi
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
import com.ozgen.navicloud.i18n.AppLanguage
import com.ozgen.navicloud.ui.LocalAppContainer
import com.ozgen.navicloud.ui.i18n.LocalStrings
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
        val internetLyrics: Boolean = true,
        // "Sadece Wi-Fi'de indir": metered (ölçülü) bağlantıda indirmeyi bekletir.
        val downloadWifiOnly: Boolean = false,
        // Mini oynatıcı: son pencere konumu (AWT ekran px) + görünüm varyantı.
        // null konum = henüz taşınmadı → varsayılan (sağ-alt) konuma çık.
        val miniX: Int? = null,
        val miniY: Int? = null,
        val miniVariant: String = MiniVariant.STANDARD.name,
        // Uzaktan kumanda: kalıcı cihaz kimliği + düzenlenebilir ad (RC-1/RC-2)
        val deviceId: String? = null,
        val deviceName: String? = null,
        // Sabit uzaktan kumanda parolası (RC-7). null/boş → PIN modu.
        val remoteSecret: String? = null,
        // Uygulama dili (SYSTEM/TURKISH/ENGLISH).
        val language: String = com.ozgen.navicloud.i18n.AppLanguage.SYSTEM.name,
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

    val internetLyricsFlow: MutableStateFlow<Boolean> = MutableStateFlow(load().internetLyrics)

    var internetLyrics: Boolean
        get() = internetLyricsFlow.value
        set(value) {
            internetLyricsFlow.value = value
            save(load().copy(internetLyrics = value))
        }

    val downloadWifiOnlyFlow: MutableStateFlow<Boolean> = MutableStateFlow(load().downloadWifiOnly)

    var downloadWifiOnly: Boolean
        get() = downloadWifiOnlyFlow.value
        set(value) {
            downloadWifiOnlyFlow.value = value
            save(load().copy(downloadWifiOnly = value))
        }

    /** Mini oynatıcının son konumu (AWT ekran px); hiç taşınmadıysa null. */
    var miniPosition: java.awt.Point?
        get() = load().let { p -> if (p.miniX != null && p.miniY != null) java.awt.Point(p.miniX, p.miniY) else null }
        set(value) = save(load().copy(miniX = value?.x, miniY = value?.y))

    var miniVariant: MiniVariant
        get() = runCatching { MiniVariant.valueOf(load().miniVariant) }.getOrDefault(MiniVariant.STANDARD)
        set(value) = save(load().copy(miniVariant = value.name))

    /** Kalıcı cihaz kimliği — ilk erişimde üretilir (uzaktan kumanda handshake/mDNS dedup). */
    val deviceId: String
        get() = load().deviceId ?: java.util.UUID.randomUUID().toString().also {
            save(load().copy(deviceId = it))
        }

    /** Cihaz seçicide görünen ad — otomatik default (hostname), ayardan düzenlenebilir. */
    var deviceName: String
        get() = load().deviceName ?: defaultDeviceName()
        set(value) = save(load().copy(deviceName = value.trim().ifBlank { null }))

    private fun defaultDeviceName(): String =
        runCatching { java.net.InetAddress.getLocalHost().hostName }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { "NaviCloud • $it" }
            ?: com.ozgen.navicloud.i18n.I18n.strings.deviceDefaultDesktopName

    /** Sabit uzaktan kumanda parolası (RC-7); boş = PIN modu. */
    var remoteSecret: String
        get() = load().remoteSecret.orEmpty()
        set(value) = save(load().copy(remoteSecret = value.trim().ifBlank { null }))

    /** Uygulama dili — UI canlı okusun diye flow; ayrıca compose-dışı kod için I18n güncellenir. */
    val languageFlow: MutableStateFlow<com.ozgen.navicloud.i18n.AppLanguage> =
        MutableStateFlow(com.ozgen.navicloud.i18n.appLanguageOf(load().language))

    var language: com.ozgen.navicloud.i18n.AppLanguage
        get() = languageFlow.value
        set(value) {
            languageFlow.value = value
            save(load().copy(language = value.name))
            com.ozgen.navicloud.i18n.I18n.language = value
        }

    init {
        // Compose-dışı toast/tepsi için başlangıç dilini yerleştir.
        com.ozgen.navicloud.i18n.I18n.language = languageFlow.value
    }
}

@Composable
fun DesktopSettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val s = LocalStrings.current
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
    var languageDialog by remember { mutableStateOf(false) }
    val language by DesktopPrefs.languageFlow.collectAsState()

    if (adding) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { adding = false }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.commonBack)
                }
                Text(s.settingsAddServer, style = MaterialTheme.typography.titleMedium)
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.commonBack)
            }
            Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall)
        }

        SectionHeader(s.settingsServersSection) {
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = s.settingsAddServer, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Icon(Icons.Rounded.Delete, contentDescription = s.commonDelete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionHeader(s.settingsPlaybackSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsStreamQuality,
            subtitle = s.streamQualityLabel(quality),
            onClick = { qualityDialog = true },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.dsettingsAudioEngine,
            subtitle = backend.label,
            onClick = { backendDialog = true },
        )
        var offline by remember { mutableStateOf(DesktopPrefs.offlineMode) }
        SettingRow(
            icon = { Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsOfflineMode,
            subtitle = s.settingsOfflineModeDesc,
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
        var internetLyrics by remember { mutableStateOf(DesktopPrefs.internetLyrics) }
        SettingRow(
            icon = { Icon(Icons.Rounded.Lyrics, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsInternetLyrics,
            subtitle = s.settingsInternetLyricsDesc,
            onClick = {
                internetLyrics = !internetLyrics
                DesktopPrefs.internetLyrics = internetLyrics
            },
            trailing = {
                Switch(checked = internetLyrics, onCheckedChange = {
                    internetLyrics = it
                    DesktopPrefs.internetLyrics = it
                })
            },
        )

        SectionHeader(s.settingsDownloadsSection)
        val dlCount by container.downloads.totalCount.collectAsState(0)
        val dlBytes by container.downloads.totalSizeBytes.collectAsState(0L)
        val activeDl by container.downloads.active.collectAsState()
        SettingRow(
            icon = { Icon(Icons.Rounded.DownloadForOffline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.commonSongCount(dlCount),
            subtitle = formatBytes(dlBytes),
            onClick = {},
            trailing = {
                if (dlCount > 0) {
                    TextButton(onClick = { scope.launch { container.downloads.clearAll() } }) {
                        Text(s.settingsDownloadDeleteAll, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
        var dlWifiOnly by remember { mutableStateOf(DesktopPrefs.downloadWifiOnly) }
        SettingRow(
            icon = { Icon(Icons.Rounded.Wifi, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsDownloadWifiOnly,
            subtitle = s.settingsDownloadWifiOnlyDesc,
            onClick = {
                dlWifiOnly = !dlWifiOnly
                DesktopPrefs.downloadWifiOnly = dlWifiOnly
            },
            trailing = {
                Switch(checked = dlWifiOnly, onCheckedChange = {
                    dlWifiOnly = it
                    DesktopPrefs.downloadWifiOnly = it
                })
            },
        )
        activeDl?.let { dl ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (dl.waitingForWifi) s.downloadWaitingForWifi(dl.title)
                    else s.downloadInProgress(dl.title) + if (dl.queued > 1) s.downloadQueuedSuffix(dl.queued - 1) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!dl.waitingForWifi) {
                    LinearProgressIndicator(
                        progress = { dl.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }

        SectionHeader(s.settingsCacheSection)
        var imageCacheBytes by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            imageCacheBytes = runCatching {
                coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache?.size ?: 0L
            }.getOrDefault(0L)
        }
        SettingRow(
            icon = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsImageCache,
            subtitle = s.settingsImageCacheDesc(formatBytes(imageCacheBytes)),
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
                    }) { Text(s.commonClear) }
                }
            },
        )

        SectionHeader(s.settingsLibrarySection)
        val scanState = scanning
        SettingRow(
            icon = { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsScanLibrary,
            subtitle = when {
                scanState?.first == true -> s.settingsScanScanning(scanState.second)
                scanState != null -> s.settingsScanDone(scanState.second)
                else -> s.settingsScanIdleDesc
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

        SectionHeader(s.settingsAppSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.settingsLanguage,
            subtitle = when (language) {
                AppLanguage.SYSTEM -> s.languageSystem
                AppLanguage.TURKISH -> s.languageTurkish
                AppLanguage.ENGLISH -> s.languageEnglish
            },
            onClick = { languageDialog = true },
        )
        var closeToTray by remember { mutableStateOf(DesktopPrefs.closeToTray) }
        SettingRow(
            icon = { Icon(Icons.Rounded.Minimize, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.dsettingsCloseToTrayTitle,
            subtitle = s.dsettingsCloseToTraySubtitle,
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

        SectionHeader(s.settingsRemoteControlSection)
        var rcSecret by remember { mutableStateOf(DesktopPrefs.remoteSecret) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                s.settingsRemoteControlDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = rcSecret,
                    onValueChange = { rcSecret = it },
                    singleLine = true,
                    placeholder = { Text(s.commonPassword) },
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Button(
                    onClick = { DesktopPrefs.remoteSecret = rcSecret },
                    enabled = rcSecret != DesktopPrefs.remoteSecret,
                ) { Text(s.commonSave) }
            }
        }

        SectionHeader(s.settingsAboutSection)
        val uriHandler = LocalUriHandler.current
        SettingRow(
            icon = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "${AppInfo.NAME} ${AppInfo.VERSION}",
            subtitle = "${AppInfo.LICENSE} · ${s.settingsSourceCode}",
            onClick = { runCatching { uriHandler.openUri(AppInfo.REPO_URL) } },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = s.licensesTitle,
            subtitle = s.dsettingsLicensesSubtitle,
            onClick = { showLicenses = true },
        )
        Spacer(Modifier.height(32.dp))
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text(s.settingsStreamQuality) },
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
                            Text(s.streamQualityLabel(q), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { qualityDialog = false }) { Text(s.commonClose) }
            },
        )
    }

    if (backendDialog) {
        AlertDialog(
            onDismissRequest = { backendDialog = false },
            title = { Text(s.dsettingsAudioEngine) },
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
                                    s.dsettingsAudioBackendDefault,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { backendDialog = false }) { Text(s.commonClose) }
            },
        )
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(s.settingsLanguage) },
            text = {
                Column {
                    AppLanguage.entries.forEach { l ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DesktopPrefs.language = l
                                    languageDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == l, onClick = {
                                DesktopPrefs.language = l
                                languageDialog = false
                            })
                            Text(
                                when (l) {
                                    AppLanguage.SYSTEM -> s.languageSystem
                                    AppLanguage.TURKISH -> s.languageTurkish
                                    AppLanguage.ENGLISH -> s.languageEnglish
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(s.commonClose) }
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
