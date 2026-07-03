package com.ozgen.navicloud.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.RepeatMode
import com.ozgen.navicloud.ui.theme.NaviCloudTheme
import kotlinx.coroutines.delay

/**
 * Her zaman üstte tutulabilen kompakt oynatıcı — telefon bildirim
 * çalarına benzer. Çerçevesiz; içerik pencereyi kenardan kenara doldurur
 * (saydam pencere bazı sistemlerde siyah zemin gösterdiği için kullanılmadı;
 * Windows 11 zaten köşeleri yuvarlar). Üst şerit sürüklenerek taşınır,
 * iğne ikonu topmost'u açar/kapatır, büyüt ikonu ana pencereye döner.
 */
@Composable
fun MiniPlayerWindow(player: PlayerController, onExpand: () -> Unit) {
    val state = rememberWindowState(
        size = DpSize(420.dp, 190.dp),
        position = WindowPosition(Alignment.BottomEnd),
    )
    var alwaysOnTop by remember { mutableStateOf(true) }

    Window(
        onCloseRequest = onExpand,
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = alwaysOnTop,
        title = "NaviCloud Mini",
    ) {
        // Çerçevesiz pencereyi taşımak için AWT ref'i
        val win = window
        // Köşe yuvarlamada (DWM) görünen zemin siyah/beyaz değil koyu olsun
        LaunchedEffect(win) { runCatching { win.background = java.awt.Color(20, 20, 26) } }

        NaviCloudTheme {
            val ps by player.state.collectAsState()
            val track = ps.currentTrack
            var posMs by remember { mutableLongStateOf(0L) }
            var durMs by remember { mutableLongStateOf(0L) }
            var lastSeek by remember { mutableLongStateOf(0L) }
            // seekTo async: hedef oturana kadar pozisyonu okumayı bastır
            LaunchedEffect(ps.isPlaying, track?.song?.id) {
                while (true) {
                    if (System.currentTimeMillis() - lastSeek > 700) posMs = player.positionMs
                    durMs = player.durationMs
                    delay(400)
                }
            }
            val accent = MaterialTheme.colorScheme.primary

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Üst şerit: sürükle → pencereyi taşı. Mutlak fare konumu
                        // deltası kullanılır; bileşen-göreli delta pencere kayınca
                        // geri besleme döngüsüne girip titreme/gecikme yapıyordu.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).pointerInput(win) {
                                var last: java.awt.Point? = null
                                detectDragGestures(
                                    onDragStart = { last = java.awt.MouseInfo.getPointerInfo()?.location },
                                    onDragEnd = { last = null },
                                    onDragCancel = { last = null },
                                ) { change, _ ->
                                    change.consume()
                                    val cur = java.awt.MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                                    val prev = last
                                    if (prev != null) {
                                        win.setLocation(win.x + (cur.x - prev.x), win.y + (cur.y - prev.y))
                                    }
                                    last = cur
                                }
                            },
                        ) {
                            if (track?.artworkUrl != null) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x22FFFFFF)),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track?.song?.title ?: "—",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track?.song?.artist ?: "",
                                    color = Color(0xB3FFFFFF),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { alwaysOnTop = !alwaysOnTop }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = "Her zaman üstte",
                                tint = if (alwaysOnTop) accent else Color(0x99FFFFFF),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        IconButton(onClick = onExpand, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Rounded.OpenInFull,
                                contentDescription = "Büyüt",
                                tint = Color(0x99FFFFFF),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                    WaveformSeekBar(
                        seedKey = track?.song?.id ?: "",
                        progress = frac,
                        accent = accent,
                        playing = ps.isPlaying,
                        onSeek = { f ->
                            if (durMs > 0) {
                                posMs = (f * durMs).toLong()
                                lastSeek = System.currentTimeMillis()
                                player.seekTo(posMs)
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(posMs), color = Color(0x99FFFFFF), style = MaterialTheme.typography.labelSmall)
                        Text(fmtTime(durMs), color = Color(0x99FFFFFF), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(2.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { player.toggleShuffle() }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Rounded.Shuffle, "Karıştır",
                                tint = if (ps.shuffle) accent else Color(0xB3FFFFFF),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { player.skipPrevious() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, "Önceki", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        FilledIconButton(
                            onClick = { player.togglePlayPause() },
                            modifier = Modifier.size(46.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0F0F14),
                            ),
                        ) {
                            Icon(
                                if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (ps.isPlaying) "Duraklat" else "Çal",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = { player.skipNext() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.SkipNext, "Sonraki", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = { player.cycleRepeat() }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (ps.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                "Tekrar",
                                tint = if (ps.repeat != RepeatMode.OFF) accent else Color(0xB3FFFFFF),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
