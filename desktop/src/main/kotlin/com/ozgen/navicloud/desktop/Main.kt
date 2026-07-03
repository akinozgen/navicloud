package com.ozgen.navicloud.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * D1 SPIKE: libmpv'nin Windows'ta JNA üzerinden Subsonic stream'i
 * çalabildiğinin kanıtı. Gerçek masaüstü UI'sı DEĞİL — D4'te rail'li
 * ortak kabuk gelecek.
 */
private class SubsonicLite(
    private val base: String,
    private val user: String,
    private val pass: String,
) {
    private val salt: String = buildString {
        val rnd = SecureRandom()
        repeat(12) { append("abcdefghijklmnopqrstuvwxyz0123456789"[rnd.nextInt(36)]) }
    }
    private val token: String =
        MessageDigest.getInstance("MD5").digest((pass + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }
    private val http = OkHttpClient()

    private fun url(endpoint: String, vararg q: Pair<String, String>): String = buildString {
        append(base.trimEnd('/')).append("/rest/").append(endpoint)
        append("?u=").append(user).append("&t=").append(token).append("&s=").append(salt)
        append("&v=1.16.1&c=NaviCloudDesktop")
        q.forEach { (k, v) -> append('&').append(k).append('=').append(v) }
    }

    /** Rastgele bir şarkı: "Sanatçı — Başlık" + auth'lu stream URL'si. */
    fun randomSong(): Pair<String, String> {
        val body = http.newCall(Request.Builder().url(url("getRandomSongs", "size" to "1", "f" to "json")).build())
            .execute().use { it.body!!.string() }
        val song = Json.parseToJsonElement(body)
            .jsonObject["subsonic-response"]!!.jsonObject["randomSongs"]!!
            .jsonObject["song"]!!.jsonArray[0].jsonObject
        val id = song["id"]!!.jsonPrimitive.content
        val title = song["title"]!!.jsonPrimitive.content
        val artist = song["artist"]?.jsonPrimitive?.content ?: "?"
        return "$artist — $title" to url("stream", "id" to id)
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "NaviCloud Desktop — mpv spike") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            var engine by remember { mutableStateOf<MpvEngine?>(null) }
            var status by remember { mutableStateOf("mpv yükleniyor…") }
            var title by remember { mutableStateOf("—") }
            var codec by remember { mutableStateOf("") }
            var paused by remember { mutableStateOf(false) }
            var posSec by remember { mutableStateOf(0.0) }
            var durSec by remember { mutableStateOf(0.0) }
            var busy by remember { mutableStateOf(false) }
            val subsonic = remember { SubsonicLite("http://example.local", "ozgen", "REDACTED") }

            LaunchedEffect(Unit) {
                status = runCatching { engine = MpvEngine(); "mpv hazır ✓" }
                    .getOrElse { "mpv YÜKLENEMEDİ: ${it.message}" }
                println("SPIKE: $status")
                // Kanıt otomatik başlasın: pencere açılınca rastgele bir şarkı çal
                engine?.let { e ->
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val (t, url) = subsonic.randomSong()
                            title = t
                            println("SPIKE: caliyor -> $t")
                            e.play(url)
                        }.onFailure {
                            println("SPIKE HATA: $it")
                            title = "Hata: ${it.message}"
                        }
                    }
                }
                while (true) {
                    engine?.let {
                        posSec = it.positionSec
                        durSec = it.durationSec
                        paused = it.isPaused
                        codec = it.audioParams ?: ""
                    }
                    delay(500)
                }
            }

            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(status, style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (codec.isNotEmpty()) Text(codec, style = MaterialTheme.typography.labelMedium)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = engine != null && !busy,
                        onClick = {
                            busy = true
                            Thread {
                                runCatching {
                                    val (t, url) = subsonic.randomSong()
                                    title = t
                                    engine?.play(url)
                                }.onFailure { title = "Hata: ${it.message}" }
                                busy = false
                            }.start()
                        },
                    ) { Text("Rastgele şarkı çal") }
                    OutlinedButton(
                        enabled = engine != null,
                        onClick = { engine?.setPaused(!paused) },
                    ) { Text(if (paused) "Devam" else "Duraklat") }
                    if (busy) CircularProgressIndicator(Modifier.width(24.dp))
                }

                Slider(
                    value = if (durSec > 0) (posSec / durSec).toFloat().coerceIn(0f, 1f) else 0f,
                    onValueChange = { f -> if (durSec > 0) engine?.seekTo(f * durSec) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(posSec))
                    Spacer(Modifier.width(8.dp))
                    Text(fmt(durSec))
                }
            }
        }
    }
}

private fun fmt(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
