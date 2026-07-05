package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozgen.navicloud.remote.ConnState
import com.ozgen.navicloud.remote.ControlTarget
import com.ozgen.navicloud.remote.RemoteControlManager
import com.ozgen.navicloud.ui.LocalAppContainer

/**
 * Spotify Connect tarzı cihaz seçici (RC-3). "Bu cihaz" + aynı Navidrome'daki keşfedilen peer'lar.
 * Meşgul peer soluk/seçilemez; mDNS engelli ağlar için manuel IP:port ekleme; cihaz adı düzenleme.
 * remoteControl null ise hiç çağrılmamalı (cast ikonu zaten gizli).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(onDismiss: () -> Unit) {
    val rc: RemoteControlManager = LocalAppContainer.current.remoteControl ?: return
    val devices by rc.devices.collectAsStateWithLifecycle()
    val target by rc.target.collectAsStateWithLifecycle()
    val connState by rc.connState.collectAsStateWithLifecycle()
    val selfName by rc.selfName.collectAsStateWithLifecycle()
    val pairedIds by rc.pairedIds.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var addingManual by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Cihaz seç",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // Bu cihaz
            DeviceRow(
                icon = Icons.Rounded.Cast,
                name = selfName,
                subtitle = "Bu cihaz",
                selected = target is ControlTarget.Local,
                enabled = true,
                trailing = {
                    IconButton(onClick = { editingName = true }) {
                        Icon(
                            Icons.Rounded.Edit, contentDescription = "Adı düzenle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                        )
                    }
                },
                onClick = { rc.controlLocal(); onDismiss() },
            )

            // Keşfedilen + manuel peer'lar
            devices.forEach { d ->
                val isTarget = (target as? ControlTarget.Remote)?.deviceId == d.deviceId
                DeviceRow(
                    icon = if (d.platform == "android") Icons.Rounded.Smartphone else Icons.Rounded.Computer,
                    name = d.name,
                    subtitle = when {
                        d.busy -> "Meşgul"
                        isTarget && connState == ConnState.CONNECTED -> "Buradan çalıyor"
                        isTarget && (connState == ConnState.CONNECTING || connState == ConnState.PAIRING) -> "Bağlanıyor…"
                        d.platform == "manual" -> "Elle eklendi"
                        else -> d.host
                    },
                    selected = isTarget && connState == ConnState.CONNECTED,
                    enabled = !d.busy,
                    trailing = {
                        when {
                            isTarget && (connState == ConnState.CONNECTING || connState == ConnState.PAIRING) ->
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            // Eşleşmişse "unut" (RC-7) — bir dahaki sefere yeniden PIN/parola ister
                            d.deviceId in pairedIds -> IconButton(onClick = { rc.forget(d.deviceId) }) {
                                Icon(
                                    Icons.Rounded.LinkOff, contentDescription = "Cihazı unut",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = { rc.controlDevice(d.deviceId) },
                )
            }

            if (devices.isEmpty()) {
                Text(
                    "Ağda başka cihaz yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            if (connState == ConnState.FAILED) {
                Text(
                    "Bağlanılamadı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { addingManual = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("IP ile ekle")
            }
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(selfName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Cihaz adı") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) rc.setSelfName(name)
                    editingName = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Vazgeç") } },
        )
    }

    if (addingManual) {
        var hostPort by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingManual = false },
            title = { Text("IP ile ekle") },
            text = {
                Column {
                    Text(
                        "Cihaz listede yoksa IP adresini yaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hostPort, onValueChange = { hostPort = it },
                        singleLine = true, placeholder = { Text("192.168.1.10") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parts = hostPort.trim().split(":")
                    val host = parts.getOrNull(0).orEmpty()
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 46464
                    if (host.isNotBlank()) rc.addManualPeer(host, port)
                    addingManual = false
                }) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { addingManual = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun DeviceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Seçili", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
