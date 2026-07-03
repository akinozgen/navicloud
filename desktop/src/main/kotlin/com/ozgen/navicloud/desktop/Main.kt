package com.ozgen.navicloud.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlaybackContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * D3 SPIKE v2: shared MusicRepository + MpvPlayerController uçtan uca.
 * Kuyruk/next/prev/seek/endless masaüstünde mpv üstünde çalışıyor.
 * Gerçek UI kabuğu D4'te.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "NaviCloud Desktop — kuyruk spike") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                var status by remember { mutableStateOf("başlatılıyor…") }
                var controller by remember { mutableStateOf<MpvPlayerController?>(null) }
                var repo by remember { mutableStateOf<MusicRepository?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    status = runCatching {
                        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
                        val servers = DesktopServerSource(
                            Server(1, "NaviTest", "http://example.local", "ozgen", "REDACTED"),
                            OkHttpClient(),
                            json,
                        )
                        val music = MusicRepository(servers, InMemoryApiCacheStore(), json, AlwaysOnlineSource())
                        repo = music
                        controller = MpvPlayerController(MpvEngine(), music)
                        "hazır ✓ (mpv + shared çekirdek)"
                    }.getOrElse { "HATA: ${it.message}" }
                    println("SPIKE2: $status")
                    // Otonom doğrulama: NAVI_SELFTEST=1 ile kuyruk akışını uçtan uca dene
                    if (System.getenv("NAVI_SELFTEST") == "1") {
                        val c2 = controller ?: return@LaunchedEffect
                        val music = repo ?: return@LaunchedEffect
                        launch(Dispatchers.IO) {
                            runCatching {
                                val songs = music.randomSongs(5)
                                c2.play(songs, 0, PlaybackContext.AllSongs, "test")
                                delay(7000)
                                var s = c2.state.value
                                println("TEST1 play: idx=${s.currentIndex} playing=${s.isPlaying} pos=${c2.positionMs}ms track=${s.currentTrack?.song?.title}")
                                c2.skipNext(); delay(5000)
                                s = c2.state.value
                                println("TEST2 next: idx=${s.currentIndex} playing=${s.isPlaying} pos=${c2.positionMs}ms")
                                c2.seekToQueueItem(3); delay(5000)
                                s = c2.state.value
                                println("TEST3 seek3: idx=${s.currentIndex} playing=${s.isPlaying}")
                                c2.togglePlayPause(); delay(1000)
                                println("TEST4 pause: playing=${c2.state.value.isPlaying}")
                                c2.stop(); delay(500)
                                println("TEST5 stop: queue=${c2.state.value.queue.size}")
                                println("SELFTEST BITTI")
                            }.onFailure { println("SELFTEST HATA: $it") }
                        }
                    }
                }

                val c = controller
                if (c == null) {
                    Text(status, Modifier.padding(24.dp))
                    return@Surface
                }
                val state by c.state.collectAsState()
                val endless by c.endless.collectAsState()
                var posSec by remember { mutableStateOf(0.0) }
                var durSec by remember { mutableStateOf(0.0) }
                LaunchedEffect(c) {
                    while (true) {
                        posSec = c.positionMs / 1000.0
                        durSec = c.durationMs / 1000.0
                        delay(500)
                    }
                }

                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Sol: kontroller
                    Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(status, style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.currentTrack?.let { "${it.song.artist} — ${it.song.title}" } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        val songs = repo!!.randomSongs(20)
                                        c.play(songs, 0, PlaybackContext.AllSongs, "Karışık çalma")
                                        println("SPIKE2: kuyruk ${songs.size} sarki")
                                    }.onFailure { println("SPIKE2 HATA: $it") }
                                }
                            }) { Text("Karışık 20 çal") }
                            OutlinedButton(onClick = { c.togglePlayPause() }) {
                                Text(if (state.isPlaying) "Duraklat" else "Devam")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { c.skipPrevious() }) { Text("◀◀") }
                            OutlinedButton(onClick = { c.skipNext() }) { Text("▶▶") }
                            OutlinedButton(onClick = { c.toggleShuffle() }) {
                                Text(if (state.shuffle) "Karışık ✓" else "Karışık")
                            }
                            OutlinedButton(onClick = { c.cycleRepeat() }) { Text("Tekrar: ${state.repeat}") }
                        }
                        Slider(
                            value = if (durSec > 0) (posSec / durSec).toFloat().coerceIn(0f, 1f) else 0f,
                            onValueChange = { f -> if (durSec > 0) c.seekTo((f * durSec * 1000).toLong()) },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fmt(posSec))
                            Text(fmt(durSec))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Otomatik oynatma")
                            Switch(checked = endless, onCheckedChange = { c.toggleEndless() })
                        }
                    }
                    // Sağ: kuyruk (tıkla → o şarkıya atla)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.queue, key = { it.uid }) { track ->
                            val current = track.uid == state.queue.getOrNull(state.currentIndex)?.uid
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (current) Color(0x2200FF88) else Color.Transparent)
                                    .clickable { c.seekToUid(track.uid) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(track.song.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    track.song.artist ?: "?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
