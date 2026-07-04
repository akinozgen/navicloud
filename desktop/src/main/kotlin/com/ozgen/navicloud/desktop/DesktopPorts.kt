package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.core.network.SubsonicClient
import com.ozgen.navicloud.core.network.unwrap
import com.ozgen.navicloud.data.ActiveDownload
import com.ozgen.navicloud.data.ApiCacheStore
import com.ozgen.navicloud.data.CachedEntry
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.data.RecentSearchesStore
import com.ozgen.navicloud.data.ServerSource
import com.ozgen.navicloud.playback.QueueStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared çekirdeğin masaüstü portları. Sunucu listesi JSON dosyada kalıcı
 * (~/.navicloud/servers.json); metadata cache ve arama geçmişi şimdilik
 * bellek-içi (D5'te kalıcı halleri).
 */
class DesktopServerSource(
    private val okHttp: OkHttpClient,
    private val json: Json,
) : ServerSource {
    private val file = File(System.getProperty("user.home"), ".navicloud/servers.json")

    @kotlinx.serialization.Serializable
    private data class Persisted(val servers: List<PersistedServer> = emptyList(), val activeId: Long? = null)

    @kotlinx.serialization.Serializable
    private data class PersistedServer(
        val id: Long, val name: String, val baseUrl: String,
        val username: String, val password: String,
    )

    private val state = MutableStateFlow(load())

    private fun load(): Persisted =
        runCatching { json.decodeFromString<Persisted>(file.readText()) }.getOrDefault(Persisted())

    private fun save(p: Persisted) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Persisted.serializer(), p))
        }
    }

    private fun PersistedServer.toModel() = Server(id, name, baseUrl, username, password)

    override val servers: Flow<List<Server>> = state.map { it.servers.map { s -> s.toModel() } }

    override val activeServer: Flow<Server?> = state.map { p ->
        (p.servers.find { it.id == p.activeId } ?: p.servers.firstOrNull())?.toModel()
    }

    private val clients = ConcurrentHashMap<Long, SubsonicClient>()

    override fun clientFor(server: Server): SubsonicClient =
        clients.getOrPut(server.id) { SubsonicClient(server, okHttp, json) }

    override suspend fun activeClient(): SubsonicClient =
        clientFor(activeServer.first() ?: error("Aktif sunucu yok"))

    override suspend fun addServer(name: String, baseUrl: String, username: String, password: String): Long {
        val candidate = Server(0, name, baseUrl.trimEnd('/'), username, password)
        SubsonicClient(candidate, okHttp, json).api.ping().unwrap()
        val p = state.value
        val id = (p.servers.maxOfOrNull { it.id } ?: 0L) + 1
        val next = p.copy(
            servers = p.servers + PersistedServer(id, name, baseUrl.trimEnd('/'), username, password),
            activeId = id,
        )
        state.value = next
        save(next)
        return id
    }

    override suspend fun setActive(id: Long) {
        val next = state.value.copy(activeId = id)
        state.value = next
        save(next)
    }

    override suspend fun removeServer(id: Long) {
        val p = state.value
        val next = p.copy(servers = p.servers.filter { it.id != id })
        state.value = next
        clients.remove(id)
        save(next)
    }
}

class InMemoryApiCacheStore : ApiCacheStore {
    private val map = ConcurrentHashMap<String, CachedEntry>()
    override suspend fun get(key: String): CachedEntry? = map[key]
    override suspend fun put(entry: CachedEntry) { map[entry.key] = entry }
    override suspend fun deleteByPrefix(prefix: String) {
        map.keys.removeAll { it.startsWith(prefix) }
    }
    override suspend fun clear() = map.clear()
    override suspend fun sizeBytes(): Long = map.values.sumOf { it.json.length.toLong() }
}

class AlwaysOnlineSource : OfflineModeSource {
    override val offlineMode: Flow<Boolean> = MutableStateFlow(false)
}

/** Ayarlardaki anahtara bağlı offline kaynağı. */
class DesktopOfflineSource : OfflineModeSource {
    override val offlineMode: Flow<Boolean> = DesktopPrefs.offlineModeFlow
}

/** İnternet (LRCLIB) sözleri ayarı. */
class DesktopLyricsSettings : com.ozgen.navicloud.data.LyricsSettings {
    override val internetLyricsEnabled: Flow<Boolean> = DesktopPrefs.internetLyricsFlow
}

/** Masaüstünde indirme MVP'de yok — boş ama zararsız implementasyon. */
class NoDownloads : DownloadsPort {
    override val downloadedIds: Flow<List<String>> = MutableStateFlow(emptyList())
    override val active: StateFlow<ActiveDownload?> = MutableStateFlow(null)
    override val totalSizeBytes: Flow<Long> = MutableStateFlow(0L)
    override val totalCount: Flow<Int> = MutableStateFlow(0)
    override fun downloadsFor(serverId: Long): Flow<List<Song>> = MutableStateFlow(emptyList())
    override suspend fun allDownloadedSongs(): List<Song> = emptyList()
    override fun enqueue(songs: List<Song>) = Unit
    override suspend fun delete(songId: String) = Unit
    override suspend fun clearAll() = Unit
}

class InMemoryRecentSearches : RecentSearchesStore {
    private val state = MutableStateFlow<List<String>>(emptyList())
    override val recents: Flow<List<String>> = state
    override suspend fun record(query: String) {
        state.value = (listOf(query) + state.value.filter { !it.equals(query, ignoreCase = true) }).take(10)
    }
    override suspend fun remove(query: String) {
        state.value = state.value.filter { !it.equals(query, ignoreCase = true) }
    }
    override suspend fun clear() { state.value = emptyList() }
}


/** Kalıcı kuyruk deposu: ~/.navicloud/queue.json */
class FileQueueStateStore : QueueStateStore {
    private val file = File(System.getProperty("user.home"), ".navicloud/queue.json")

    override suspend fun save(json: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    override suspend fun load(): String? =
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    override suspend fun clear() {
        runCatching { file.delete() }
    }
}

/** Kuyruk senkronu durumu: ~/.navicloud/queue_sync.json */
class FileQueueSyncStateStore : com.ozgen.navicloud.playback.QueueSyncStateStore {
    private val file = File(System.getProperty("user.home"), ".navicloud/queue_sync.json")

    override suspend fun save(json: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    override suspend fun load(): String? =
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()
}
