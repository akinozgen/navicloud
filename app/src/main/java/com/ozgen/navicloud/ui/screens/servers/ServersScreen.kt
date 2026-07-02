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
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.GraphicEq
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
import com.ozgen.navicloud.ui.screens.login.LoginScreen
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
    private val music: MusicRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        serverRepo.servers,
        serverRepo.activeServer,
        settings.streamQuality,
        settings.offlineMode,
        combine(downloads.totalCount, downloads.totalSizeBytes) { c, b -> c to b },
    ) { servers, active, quality, offline, dl ->
        SettingsUiState(servers, active?.id, quality, offline, dl.first, dl.second)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    val activeDownload: StateFlow<ActiveDownload?> = downloads.active

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

@Composable
fun ServersScreen(navController: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active by vm.activeDownload.collectAsStateWithLifecycle()
    val scan by vm.scanning.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    if (adding) {
        // Login formu; başarılı bağlantı yeni sunucuyu aktif yapar
        LoginScreen()
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
            }
            Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        }

        // ---- SUNUCULAR ----
        SectionHeader("Sunucular") {
            IconButton(onClick = { adding = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Sunucu ekle",
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
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- ÇALMA ----
        SectionHeader("Çalma")
        SettingRow(
            icon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Akış kalitesi",
            subtitle = state.quality.label,
            onClick = { qualityDialog = true },
        )
        SettingRow(
            icon = { Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Offline mod",
            subtitle = "Yalnızca indirilenlerden çalar, ağı kullanmaz",
            onClick = { vm.setOffline(!state.offlineMode) },
            trailing = {
                Switch(checked = state.offlineMode, onCheckedChange = { vm.setOffline(it) })
            },
        )

        // ---- KÜTÜPHANE ----
        SectionHeader("Kütüphane")
        val scanState = scan
        SettingRow(
            icon = { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Kütüphaneyi tara",
            subtitle = when {
                scanState?.first == true -> "Taranıyor… ${scanState.second} öğe"
                scanState != null -> "Tamamlandı • ${scanState.second} öğe"
                else -> "Sunucuda yeni dosyaları bulur"
            },
            onClick = { vm.startScan(full = false) },
            trailing = {
                if (scanState?.first == true) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { vm.startScan(full = true) }) { Text("Tam tarama") }
                }
            },
        )

        // ---- İNDİRMELER ----
        SectionHeader("İndirmeler")
        SettingRow(
            icon = { Icon(Icons.Rounded.DownloadForOffline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "${state.downloadCount} şarkı",
            subtitle = formatBytes(state.downloadBytes),
            onClick = {},
            trailing = {
                if (state.downloadCount > 0) {
                    TextButton(onClick = { clearDialog = true }) {
                        Text("Tümünü sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
        val activeDl = active
        if (activeDl != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "İndiriliyor: ${activeDl.title}" +
                        if (activeDl.queued > 1) " (+${activeDl.queued - 1} sırada)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { activeDl.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
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
                            Text(q.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        "Orijinal dışındaki seçimlerde sunucu MP3'e dönüştürür — hücresel veride kotayı korur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qualityDialog = false }) { Text("Kapat") }
            },
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Tüm indirilenler silinsin mi?") },
            text = { Text("${state.downloadCount} şarkı (${formatBytes(state.downloadBytes)}) cihazdan kaldırılacak.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearDownloads()
                    clearDialog = false
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text("Vazgeç") }
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
