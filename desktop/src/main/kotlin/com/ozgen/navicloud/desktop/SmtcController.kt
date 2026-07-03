package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedWriter
import java.io.File

/**
 * Windows System Media Transport Controls (SMTC) entegrasyonu.
 *
 * Native yardımcı süreci (navicloud-smtc.exe) başlatır; ona parça bilgisi +
 * oynatma durumu gönderir (stdin), medya tuşu / flyout buton olaylarını alır
 * (stdout) ve [PlayerController]'a bağlar. Böylece kontrol merkezi/flyout'ta
 * kapak+başlık+sanatçı ve donanım medya tuşları çalışır.
 *
 * Yardımcı bulunamazsa sessizce devre dışı kalır (çökme yok).
 */
class SmtcController(
    private val player: PlayerController,
    private val okHttp: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    private val coverFile = File(System.getProperty("user.home"), ".navicloud/smtc_cover.jpg")

    fun start() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val exe = resolveHelper() ?: run {
            println("SMTC: navicloud-smtc.exe bulunamadı, entegrasyon atlandı")
            return
        }
        val p = try {
            ProcessBuilder(exe.absolutePath).redirectErrorStream(false).start()
        } catch (e: Exception) {
            println("SMTC: yardımcı başlatılamadı: ${e.message}")
            return
        }
        process = p
        writer = p.outputStream.bufferedWriter()

        Runtime.getRuntime().addShutdownHook(Thread { runCatching { p.destroyForcibly() } })

        // Buton olayları: helper stdout -> player
        scope.launch {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    when (line.trim()) {
                        "PLAY" -> if (!player.state.value.isPlaying) player.togglePlayPause()
                        "PAUSE" -> if (player.state.value.isPlaying) player.togglePlayPause()
                        "NEXT" -> player.skipNext()
                        "PREV" -> player.skipPrevious()
                        "STOP" -> player.stop()
                    }
                }
            }
        }

        // Parça/durum değişimi -> helper
        scope.launch { observeState() }
    }

    private var lastId: String? = null
    private var lastPlaying: Boolean? = null

    private suspend fun observeState() {
        player.state.collect { state ->
            val track = state.currentTrack
            if (track == null) {
                if (lastId != null) {
                    send("S\tstopped")
                    lastId = null
                    lastPlaying = null
                }
                return@collect
            }
            if (track.song.id != lastId) {
                lastId = track.song.id
                val cover = downloadCover(track.artworkUrl)
                send(
                    "M\t${clean(track.song.title)}\t${clean(track.song.artist ?: "")}\t" +
                        "${clean(track.song.album ?: "")}\t${cover ?: ""}",
                )
            }
            if (state.isPlaying != lastPlaying) {
                lastPlaying = state.isPlaying
                send("S\t${if (state.isPlaying) "playing" else "paused"}")
            }
        }
    }

    /** Kapağı yerel dosyaya indirir; SMTC thumbnail için dosya yolu gerekir. */
    private fun downloadCover(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: return null
                coverFile.parentFile?.mkdirs()
                coverFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            coverFile.absolutePath
        }.getOrNull()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    private fun send(line: String) {
        val w = writer ?: return
        runCatching {
            w.write(line)
            w.newLine()
            w.flush()
        }
    }

    /** navicloud-smtc.exe'yi libmpv ile aynı mantıkla arar. */
    private fun resolveHelper(): File? {
        val cwd = File(System.getProperty("user.dir"))
        val packaged = System.getProperty("compose.application.resources.dir")?.let { File(it) }
        val candidates = listOf(
            packaged, cwd, File(cwd, "libs"), File(cwd, "desktop/libs"),
            cwd.parentFile?.let { File(it, "desktop/libs") },
        )
        return candidates.filterNotNull()
            .map { File(it, "navicloud-smtc.exe") }
            .firstOrNull { it.exists() }
    }
}
