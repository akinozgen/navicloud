package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.core.network.SubsonicClient
import com.ozgen.navicloud.data.ApiCacheStore
import com.ozgen.navicloud.data.CachedEntry
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.data.ServerSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared çekirdeğin masaüstü portları — MVP halleri. Kalıcı sunucu
 * kaydı/ayar deposu D5'te gelecek; şimdilik tek sabit sunucu ve
 * bellek-içi metadata cache.
 */
class DesktopServerSource(
    server: Server,
    private val okHttp: OkHttpClient,
    private val json: Json,
) : ServerSource {
    private val _active = MutableStateFlow<Server?>(server)
    override val activeServer: Flow<Server?> = _active

    private val clients = ConcurrentHashMap<Long, SubsonicClient>()

    override fun clientFor(server: Server): SubsonicClient =
        clients.getOrPut(server.id) { SubsonicClient(server, okHttp, json) }

    override suspend fun activeClient(): SubsonicClient =
        clientFor(activeServer.first() ?: error("Aktif sunucu yok"))
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
