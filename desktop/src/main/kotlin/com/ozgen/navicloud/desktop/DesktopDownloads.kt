package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.ActiveDownload
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.ServerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Masaüstü indirme sistemi — Android DownloadRepository'nin JVM karşılığı.
 * Dosyalar ~/.navicloud/music/{serverId}/, kayıt defteri downloads.json.
 * KALICI depo: akış/görsel cache'lerinden tamamen ayrı, eviction yok.
 */
class DesktopDownloads(
    private val servers: ServerSource,
    private val okHttp: OkHttpClient,
) : DownloadsPort {
    @Serializable
    private data class Entry(
        val songId: String,
        val serverId: Long,
        val title: String,
        val artist: String? = null,
        val album: String? = null,
        val albumId: String? = null,
        val coverArt: String? = null,
        val duration: Int = 0,
        val track: Int? = null,
        val filePath: String,
        val sizeBytes: Long = 0,
        val downloadedAt: Long = 0,
    )

    private val root = File(System.getProperty("user.home"), ".navicloud")
    private val indexFile = File(root, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Song>(Channel.UNLIMITED)
    private var queuedCount = 0

    private val entries = MutableStateFlow(loadIndex())

    private fun loadIndex(): List<Entry> = runCatching {
        json.decodeFromString<List<Entry>>(indexFile.readText())
            .filter { File(it.filePath).exists() }
    }.getOrDefault(emptyList())

    private fun saveIndex() {
        runCatching {
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(Entry.serializer()),
                    entries.value,
                )
            )
        }
    }

    private val _active = MutableStateFlow<ActiveDownload?>(null)
    override val active: StateFlow<ActiveDownload?> = _active

    override val downloadedIds: Flow<List<String>> = entries.map { list -> list.map { it.songId } }
    override val totalSizeBytes: Flow<Long> = entries.map { list -> list.sumOf { it.sizeBytes } }
    override val totalCount: Flow<Int> = entries.map { it.size }

    override fun downloadsFor(serverId: Long): Flow<List<Song>> =
        entries.map { list -> list.filter { it.serverId == serverId }.map { it.toSong() } }

    override suspend fun allDownloadedSongs(): List<Song> = entries.value.map { it.toSong() }

    /** Player için: indirilmişse yerel dosya yolu. */
    fun localPath(songId: String): String? =
        entries.value.firstOrNull { it.songId == songId }?.filePath
            ?.takeIf { File(it).exists() }

    init {
        scope.launch {
            for (song in queue) {
                runCatching { download(song) }
                    .onFailure { println("İndirme hatası: ${song.title} — ${it.message}") }
                queuedCount = (queuedCount - 1).coerceAtLeast(0)
                if (queuedCount == 0) _active.value = null
            }
        }
    }

    override fun enqueue(songs: List<Song>) {
        scope.launch {
            val have = entries.value.map { it.songId }.toSet()
            for (song in songs) {
                if (song.id !in have) {
                    queuedCount++
                    queue.send(song)
                }
            }
        }
    }

    override suspend fun delete(songId: String) {
        entries.value.firstOrNull { it.songId == songId }?.let { File(it.filePath).delete() }
        entries.value = entries.value.filter { it.songId != songId }
        saveIndex()
    }

    override suspend fun clearAll() {
        entries.value.forEach { File(it.filePath).delete() }
        entries.value = emptyList()
        saveIndex()
    }

    private suspend fun download(song: Song) {
        val server = servers.activeServer.first() ?: return
        val client = servers.clientFor(server)
        _active.value = ActiveDownload(song.id, song.title, 0f, queuedCount)

        val dir = File(root, "music/${server.id}").apply { mkdirs() }
        val target = File(dir, "${song.id}.${song.suffix ?: "mp3"}")
        val tmp = File(dir, "${song.id}.tmp")

        val request = Request.Builder().url(client.downloadUrl(song.id)).build()
        client.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("İndirme başarısız: HTTP ${response.code}")
            val body = response.body ?: error("Boş yanıt")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastEmit > 150) {
                            lastEmit = now
                            _active.value = ActiveDownload(song.id, song.title, copied.toFloat() / total, queuedCount)
                        }
                    }
                }
            }
        }
        tmp.copyTo(target, overwrite = true)
        tmp.delete()

        entries.value = entries.value + Entry(
            songId = song.id,
            serverId = server.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId,
            coverArt = song.coverArt,
            duration = song.duration,
            track = song.track,
            filePath = target.absolutePath,
            sizeBytes = target.length(),
            downloadedAt = System.currentTimeMillis(),
        )
        saveIndex()
    }

    private fun Entry.toSong(): Song = Song(
        id = songId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = null,
        coverArt = coverArt,
        duration = duration,
        track = track,
        discNumber = null,
        year = null,
        bitRate = null,
        suffix = File(filePath).extension.ifBlank { null },
        contentType = null,
        size = sizeBytes,
        starred = false,
    )
}
