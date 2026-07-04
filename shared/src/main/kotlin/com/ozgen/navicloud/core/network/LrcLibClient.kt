package com.ozgen.navicloud.core.network

import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.core.model.LyricsLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

@Serializable
private data class LrcLibDto(
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val duration: Double? = null,
)

/**
 * LRCLIB (lrclib.net) — sunucuda söz yoksa internet fallback. Auth yok; sanatçı+başlık
 * (+albüm/süre) ile eşleşir. `syncedLyrics` (LRC) varsa senkron, yoksa düz. Tüm çağrılar
 * sessiz fail (hata → null), çökmez.
 */
class LrcLibClient(
    private val okHttp: OkHttpClient,
    private val json: Json,
) {
    private val base = "https://lrclib.net/api".toHttpUrl()

    suspend fun fetch(artist: String, title: String, album: String?, durationSec: Int?): Lyrics? =
        withContext(Dispatchers.IO) {
            getExact(artist, title, album, durationSec)?.let { return@withContext it }
            search(artist, title, durationSec)
        }

    private fun getExact(artist: String, title: String, album: String?, durationSec: Int?): Lyrics? {
        val url = base.newBuilder().addPathSegment("get")
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSec != null && durationSec > 0) addQueryParameter("duration", durationSec.toString())
            }
            .build()
        val body = requestBody(url) ?: return null
        return runCatching { json.decodeFromString(LrcLibDto.serializer(), body) }.getOrNull()?.toLyrics()
    }

    private fun search(artist: String, title: String, durationSec: Int?): Lyrics? {
        val url = base.newBuilder().addPathSegment("search")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = requestBody(url) ?: return null
        val list = runCatching { json.decodeFromString(ListSerializer(LrcLibDto.serializer()), body) }
            .getOrNull().orEmpty()
        if (list.isEmpty()) return null
        // Süre biliniyorsa ±3sn synced tercih; yoksa ilk synced; o da yoksa ilk düz.
        val best = durationSec?.let { d ->
            list.firstOrNull { it.syncedLyrics != null && it.duration != null && abs(it.duration - d) <= 3 }
        } ?: list.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: list.firstOrNull()
        return best?.toLyrics()
    }

    private fun requestBody(url: HttpUrl): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        okHttp.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    private fun LrcLibDto.toLyrics(): Lyrics? {
        if (instrumental) return null
        syncedLyrics?.takeIf { it.isNotBlank() }?.let { synced ->
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return Lyrics(synced = true, lines = lines)
        }
        plainLyrics?.takeIf { it.isNotBlank() }?.let { plain ->
            val lines = plain.split("\n").map { LyricsLine(null, it.trim()) }
            if (lines.isNotEmpty()) return Lyrics(synced = false, lines = lines)
        }
        return null
    }

    companion object {
        private const val USER_AGENT = "NaviCloud (https://github.com/akinozgen/navicloud)"
        private val LRC_TS = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** [mm:ss.xx] etiketli LRC → zaman sıralı satırlar (çoklu etiketli satır desteklenir). */
        fun parseLrc(text: String): List<LyricsLine> {
            val out = ArrayList<LyricsLine>()
            for (raw in text.split("\n")) {
                val matches = LRC_TS.findAll(raw).toList()
                if (matches.isEmpty()) continue // metadata satırları ([ar:], [ti:], [by:]) atlanır
                val content = raw.substring(matches.last().range.last + 1).trim()
                for (m in matches) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val fracRaw = m.groupValues[3]
                    val frac = when (fracRaw.length) {
                        0 -> 0L
                        1 -> fracRaw.toLong() * 100
                        2 -> fracRaw.toLong() * 10
                        else -> fracRaw.take(3).toLong()
                    }
                    out.add(LyricsLine((min * 60 + sec) * 1000 + frac, content))
                }
            }
            return out.sortedBy { it.startMs ?: 0 }
        }
    }
}
