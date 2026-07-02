package com.ozgen.navicloud.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.db.DownloadDao
import com.ozgen.navicloud.data.db.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the in-flight download, for progress UI. */
data class ActiveDownload(
    val songId: String,
    val title: String,
    val progress: Float,
    val queued: Int,
    /** "Sadece WiFi'de indir" açık ve ağ metered: kuyruk WiFi'yi bekliyor. */
    val waitingForWifi: Boolean = false,
)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val servers: ServerRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Song>(Channel.UNLIMITED)
    private var queuedCount = 0

    private val _active = MutableStateFlow<ActiveDownload?>(null)
    val active: StateFlow<ActiveDownload?> = _active

    val downloadedIds: Flow<List<String>> = downloadDao.observeIds()
    val totalSizeBytes: Flow<Long> = downloadDao.observeTotalSize()
    val totalCount: Flow<Int> = downloadDao.observeCount()

    fun downloadsFor(serverId: Long): Flow<List<DownloadEntity>> = downloadDao.observeAll(serverId)

    /** Offline endless için: indirilen tüm şarkılar (Song modeli). */
    suspend fun allDownloadedSongs(): List<Song> = downloadDao.all().map { it.toSong() }

    /** Tüm indirilenleri siler (dosyalar + kayıtlar). */
    suspend fun clearAll() {
        downloadDao.all().forEach { File(it.filePath).delete() }
        downloadDao.deleteAll()
    }

    init {
        scope.launch {
            for (song in queue) {
                awaitAllowedNetwork(song)
                runCatching { download(song) }
                    .onFailure { android.util.Log.e("NaviDownload", "İndirme hatası: ${song.title}", it) }
                queuedCount = (queuedCount - 1).coerceAtLeast(0)
                if (queuedCount == 0) _active.value = null
            }
        }
    }

    fun enqueue(songs: List<Song>) {
        scope.launch {
            for (song in songs) {
                if (downloadDao.byId(song.id) == null) {
                    queuedCount++
                    queue.send(song)
                }
            }
        }
    }

    /** Varsayılan ağın metered durumu; değişimlerde anında yeni değer basar. */
    private fun meteredFlow(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }

            override fun onLost(network: Network) {
                trySend(cm.isActiveNetworkMetered)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        trySend(cm.isActiveNetworkMetered)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    /**
     * "Sadece WiFi'de indir" açıkken metered ağda kuyruğu bekletir;
     * WiFi gelince (ya da ayar kapatılınca) kendiliğinden devam eder.
     */
    private suspend fun awaitAllowedNetwork(song: Song) {
        val blocked = combine(meteredFlow(), settings.downloadWifiOnly) { metered, wifiOnly ->
            metered && wifiOnly
        }
        if (blocked.first()) {
            _active.value = ActiveDownload(song.id, song.title, 0f, queuedCount, waitingForWifi = true)
            blocked.first { !it }
        }
    }

    suspend fun localFile(songId: String): File? {
        val entity = downloadDao.byId(songId) ?: return null
        val file = File(entity.filePath)
        if (!file.exists()) {
            downloadDao.delete(songId)
            return null
        }
        return file
    }

    suspend fun delete(songId: String) {
        downloadDao.byId(songId)?.let { File(it.filePath).delete() }
        downloadDao.delete(songId)
    }

    private suspend fun download(song: Song) {
        val server = servers.activeServer.first() ?: return
        val client = servers.clientFor(server)
        _active.value = ActiveDownload(song.id, song.title, 0f, queuedCount)

        val dir = File(context.filesDir, "music/${server.id}").apply { mkdirs() }
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
                        // Her 64KB'da state basmak UI'ı recomposition fırtınasına
                        // boğuyordu — en fazla ~6 emisyon/sn
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastEmit > 150) {
                            lastEmit = now
                            _active.value = ActiveDownload(
                                song.id, song.title, copied.toFloat() / total, queuedCount,
                            )
                        }
                    }
                }
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Dosya taşınamadı")
        }

        downloadDao.insert(
            DownloadEntity(
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
        )
    }
}

fun DownloadEntity.toSong(): Song = Song(
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
