package com.ozgen.navicloud.ui.screens.servers

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.data.ActiveDownload
import com.ozgen.navicloud.data.DownloadRepository
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.ServerRepository
import com.ozgen.navicloud.data.SettingsRepository
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.i18n.AppLanguage
import com.ozgen.navicloud.ui.i18n.LocalStrings
import com.ozgen.navicloud.ui.screens.login.LoginScreen
import com.ozgen.navicloud.ui.screens.settings.LicensesScreen
import com.ozgen.navicloud.ui.screens.settings.androidLicenses
import com.ozgen.navicloud.ui.screens.settings.commonLicenses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val servers: List<Server> = emptyList(),
    val activeId: Long? = null,
    val quality: StreamQuality = StreamQuality.RAW,
    val offlineMode: Boolean = false,
    val downloadCount: Int = 0,
    val downloadBytes: Long = 0L,
    val streamCacheMaxMb: Int = 512,
    val downloadWifiOnly: Boolean = true,
    val prefetchEnabled: Boolean = true,
    val prefetchWifiOnly: Boolean = true,
    val internetLyrics: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val serverRepo: ServerRepository,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
    private val music: MusicRepository,
    private val streamCache: com.ozgen.navicloud.playback.StreamCache,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        serverRepo.servers,
        serverRepo.activeServer,
        combine(settings.streamQuality, settings.offlineMode, settings.streamCacheMaxMb) { q, o, mb -> Triple(q, o, mb) },
        combine(settings.downloadWifiOnly, settings.prefetchEnabled, settings.prefetchWifiOnly) { w, p, pw -> Triple(w, p, pw) },
        combine(downloads.totalCount, downloads.totalSizeBytes, settings.internetLyricsEnabled) { c, b, il -> Triple(c, b, il) },
    ) { servers, active, playback, net, dl ->
        SettingsUiState(
            servers = servers,
            activeId = active?.id,
            quality = playback.first,
            offlineMode = playback.second,
            downloadCount = dl.first,
            downloadBytes = dl.second,
            streamCacheMaxMb = playback.third,
            downloadWifiOnly = net.first,
            prefetchEnabled = net.second,
            prefetchWifiOnly = net.third,
            internetLyrics = dl.third,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    val activeDownload: StateFlow<ActiveDownload?> = downloads.active

    // Önbellek boyutları — disk ölçümü IO'da, ekrana girişte tazelenir
    private val _streamCacheBytes = MutableStateFlow(0L)
    val streamCacheBytes: StateFlow<Long> = _streamCacheBytes
    private val _imageCacheBytes = MutableStateFlow(0L)
    val imageCacheBytes: StateFlow<Long> = _imageCacheBytes

    fun refreshCacheSizes() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _streamCacheBytes.value = streamCache.cacheSpaceBytes()
            _imageCacheBytes.value = coil3.SingletonImageLoader.get(context).diskCache?.size ?: 0L
        }
    }

    fun clearStreamCache() = viewModelScope.launch {
        streamCache.clear()
        refreshCacheSizes()
    }

    fun clearImageCache() {
        val loader = coil3.SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            loader.diskCache?.clear()
            refreshCacheSizes()
        }
    }

    // Uzaktan kumanda sabit parolası (RC-7)
    val rcSecret: StateFlow<String> = settings.rcSecret.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setRcSecret(s: String) = viewModelScope.launch { settings.setRcSecret(s) }

    // Uygulama dili
    val language: StateFlow<AppLanguage> =
        settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.SYSTEM)
    fun setLanguage(l: AppLanguage) = viewModelScope.launch { settings.setLanguage(l) }

    fun setStreamCacheMax(mb: Int) = viewModelScope.launch { settings.setStreamCacheMaxMb(mb) }
    fun setInternetLyrics(on: Boolean) = viewModelScope.launch { settings.setInternetLyrics(on) }
    fun setDownloadWifiOnly(on: Boolean) = viewModelScope.launch { settings.setDownloadWifiOnly(on) }
    fun setPrefetchEnabled(on: Boolean) = viewModelScope.launch { settings.setPrefetchEnabled(on) }
    fun setPrefetchWifiOnly(on: Boolean) = viewModelScope.launch { settings.setPrefetchWifiOnly(on) }

    // Kütüphane taraması
    private val _scanning = MutableStateFlow<Pair<Boolean, Long>?>(null)
    val scanning: StateFlow<Pair<Boolean, Long>?> = _scanning

    fun setActive(id: Long) = viewModelScope.launch { serverRepo.setActive(id) }
    fun removeServer(id: Long) = viewModelScope.launch { serverRepo.removeServer(id) }
    fun setQuality(q: StreamQuality) = viewModelScope.launch { settings.setStreamQuality(q) }
    fun setOffline(on: Boolean) = viewModelScope.launch { settings.setOfflineMode(on) }
    fun clearDownloads() = viewModelScope.launch { downloads.clearAll() }

    fun startScan(full: Boolean) {
        viewModelScope.launch {
            runCatching { music.startScan(full) }.onSuccess { _scanning.value = it }
            // Tarama bitene kadar durum takibi
            while (_scanning.value?.first == true) {
                delay(1500)
                runCatching { music.scanStatus() }.onSuccess { _scanning.value = it }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}

private fun formatMb(mb: Int): String =
    if (mb >= 1024) "%.0f GB".format(mb / 1024.0) else "$mb MB"

@Composable
fun ServersScreen(navController: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active by vm.activeDownload.collectAsStateWithLifecycle()
    val scan by vm.scanning.collectAsStateWithLifecycle()
    val streamCacheBytes by vm.streamCacheBytes.collectAsStateWithLifecycle()
    val imageCacheBytes by vm.imageCacheBytes.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var cacheSizeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    val language by vm.language.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshCacheSizes() }

    // Sunucu ekleme formundayken geri tuşu formu kapatır, Ayarlar'dan çıkarmaz
    androidx.activity.compose.BackHandler(enabled = adding) { adding = false }
    androidx.activity.compose.BackHandler(enabled = showLicenses) { showLicenses = false }

    if (adding) {
        // Login formu; başarılı bağlantı yeni sunucuyu aktif yapar
        LoginScreen()
        return
    }

    if (showLicenses) {
        LicensesScreen(entries = commonLicenses + androidLicenses, onBack = { showLicenses = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.commonBack)
            }
            Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall)
        }

        // ---- SUNUCULAR ----
        SectionHeader(strings.settingsServersSection) {
            IconButton(onClick = { adding = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = strings.settingsAddServer,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.servers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.setActive(server.id) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = server.id == state.activeId, onClick = { vm.setActive(server.id) })
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${server.username} @ ${server.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.servers.size > 1) {
                    IconButton(onClick = { vm.removeServer(server.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = strings.commonDelete,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- ÇALMA ----
        SectionHeader(strings.settingsPlaybackSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsStreamQuality,
            subtitle = strings.streamQualityLabel(state.quality),
            onClick = { qualityDialog = true },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsOfflineMode,
            subtitle = strings.settingsOfflineModeDesc,
            onClick = { vm.setOffline(!state.offlineMode) },
            trailing = {
                Switch(checked = state.offlineMode, onCheckedChange = { vm.setOffline(it) })
            },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.Downloading, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsPrefetch,
            subtitle = strings.settingsPrefetchDesc,
            onClick = { vm.setPrefetchEnabled(!state.prefetchEnabled) },
            trailing = {
                Switch(checked = state.prefetchEnabled, onCheckedChange = { vm.setPrefetchEnabled(it) })
            },
        )
        if (state.prefetchEnabled) {
            SettingRow(
                icon = { Icon(Icons.Rounded.Wifi, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = strings.settingsPrefetchWifiOnly,
                subtitle = strings.settingsPrefetchWifiOnlyDesc,
                onClick = { vm.setPrefetchWifiOnly(!state.prefetchWifiOnly) },
                trailing = {
                    Switch(checked = state.prefetchWifiOnly, onCheckedChange = { vm.setPrefetchWifiOnly(it) })
                },
            )
        }

        SettingRow(
            icon = { Icon(Icons.Rounded.Lyrics, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsInternetLyrics,
            subtitle = strings.settingsInternetLyricsDesc,
            onClick = { vm.setInternetLyrics(!state.internetLyrics) },
            trailing = {
                Switch(checked = state.internetLyrics, onCheckedChange = { vm.setInternetLyrics(it) })
            },
        )

        // ---- KÜTÜPHANE ----
        SectionHeader(strings.settingsLibrarySection)
        val scanState = scan
        SettingRow(
            icon = { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsScanLibrary,
            subtitle = when {
                scanState?.first == true -> strings.settingsScanScanning(scanState.second)
                scanState != null -> strings.settingsScanDone(scanState.second)
                else -> strings.settingsScanIdleDesc
            },
            onClick = { vm.startScan(full = false) },
            trailing = {
                if (scanState?.first == true) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { vm.startScan(full = true) }) { Text(strings.settingsScanFull) }
                }
            },
        )

        // ---- ÖNBELLEK ----
        SectionHeader(strings.settingsCacheSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsStreamCache,
            subtitle = "${formatBytes(streamCacheBytes)} / ${formatMb(state.streamCacheMaxMb)}",
            onClick = { cacheSizeDialog = true },
            trailing = {
                if (streamCacheBytes > 0) {
                    TextButton(onClick = { vm.clearStreamCache() }) { Text(strings.commonClear) }
                }
            },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsImageCache,
            subtitle = strings.settingsImageCacheDesc(formatBytes(imageCacheBytes)),
            onClick = {},
            trailing = {
                if (imageCacheBytes > 0) {
                    TextButton(onClick = { vm.clearImageCache() }) { Text(strings.commonClear) }
                }
            },
        )

        // ---- İNDİRMELER ----
        SectionHeader(strings.settingsDownloadsSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.Wifi, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsDownloadWifiOnly,
            subtitle = strings.settingsDownloadWifiOnlyDesc,
            onClick = { vm.setDownloadWifiOnly(!state.downloadWifiOnly) },
            trailing = {
                Switch(checked = state.downloadWifiOnly, onCheckedChange = { vm.setDownloadWifiOnly(it) })
            },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.DownloadForOffline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.commonSongCount(state.downloadCount),
            subtitle = formatBytes(state.downloadBytes),
            onClick = {},
            trailing = {
                if (state.downloadCount > 0) {
                    TextButton(onClick = { clearDialog = true }) {
                        Text(strings.settingsDownloadDeleteAll, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
        val activeDl = active
        if (activeDl != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (activeDl.waitingForWifi) {
                        strings.downloadWaitingForWifi(activeDl.title) +
                            if (activeDl.queued > 1) strings.downloadQueuedSuffix(activeDl.queued - 1) else ""
                    } else {
                        strings.downloadInProgress(activeDl.title) +
                            if (activeDl.queued > 1) strings.downloadQueuedSuffix(activeDl.queued - 1) else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeDl.waitingForWifi) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { activeDl.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }

        // ---- UZAKTAN KUMANDA ----
        SectionHeader(strings.settingsRemoteControlSection)
        val rcSecret by vm.rcSecret.collectAsStateWithLifecycle()
        var rcField by remember(rcSecret) { mutableStateOf(rcSecret) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                strings.settingsRemoteControlDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = rcField,
                    onValueChange = { rcField = it },
                    singleLine = true,
                    placeholder = { Text(strings.commonPassword) },
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Button(
                    onClick = { vm.setRcSecret(rcField) },
                    enabled = rcField != rcSecret,
                ) { Text(strings.commonSave) }
            }
        }

        // ---- UYGULAMA ----
        SectionHeader(strings.settingsAppSection)
        SettingRow(
            icon = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.settingsLanguage,
            subtitle = when (language) {
                AppLanguage.SYSTEM -> strings.languageSystem
                AppLanguage.TURKISH -> strings.languageTurkish
                AppLanguage.ENGLISH -> strings.languageEnglish
            },
            onClick = { languageDialog = true },
        )

        // ---- HAKKINDA ----
        SectionHeader(strings.settingsAboutSection)
        val uriHandler = LocalUriHandler.current
        SettingRow(
            icon = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "${AppInfo.NAME} ${AppInfo.VERSION}",
            subtitle = "${AppInfo.LICENSE} · ${strings.settingsSourceCode}",
            onClick = { runCatching { uriHandler.openUri(AppInfo.REPO_URL) } },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = strings.licensesTitle,
            subtitle = strings.settingsLicensesDesc,
            onClick = { showLicenses = true },
        )
        Spacer(Modifier.height(32.dp))
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text(strings.settingsStreamQuality) },
            text = {
                Column {
                    StreamQuality.entries.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setQuality(q)
                                    qualityDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.quality == q, onClick = {
                                vm.setQuality(q)
                                qualityDialog = false
                            })
                            Text(strings.streamQualityLabel(q), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        strings.settingsStreamQualityDialogNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qualityDialog = false }) { Text(strings.commonClose) }
            },
        )
    }

    if (cacheSizeDialog) {
        AlertDialog(
            onDismissRequest = { cacheSizeDialog = false },
            title = { Text(strings.settingsCacheLimitDialogTitle) },
            text = {
                Column {
                    listOf(256, 512, 1024, 2048).forEach { mb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setStreamCacheMax(mb)
                                    cacheSizeDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.streamCacheMaxMb == mb, onClick = {
                                vm.setStreamCacheMax(mb)
                                cacheSizeDialog = false
                            })
                            Text(formatMb(mb), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        strings.settingsCacheLimitDialogNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { cacheSizeDialog = false }) { Text(strings.commonClose) }
            },
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(strings.settingsClearDownloadsDialogTitle) },
            text = { Text(strings.settingsClearDownloadsBody(state.downloadCount, formatBytes(state.downloadBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearDownloads()
                    clearDialog = false
                }) { Text(strings.commonDelete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text(strings.commonCancel) }
            },
        )
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(strings.settingsLanguage) },
            text = {
                Column {
                    AppLanguage.entries.forEach { l ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setLanguage(l)
                                    languageDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == l, onClick = {
                                vm.setLanguage(l)
                                languageDialog = false
                            })
                            Text(
                                when (l) {
                                    AppLanguage.SYSTEM -> strings.languageSystem
                                    AppLanguage.TURKISH -> strings.languageTurkish
                                    AppLanguage.ENGLISH -> strings.languageEnglish
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) { Text(strings.commonClose) }
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
