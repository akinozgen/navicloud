package com.ozgen.navicloud.data

import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.core.network.SubsonicClient
import kotlinx.coroutines.flow.Flow

/**
 * Paylaşılan çekirdeğin platforma açılan kapıları. Android tarafında
 * Room/DataStore, masaüstünde dosya/SQLite implemente eder — repository'ler
 * yalnızca bu arayüzleri bilir.
 */
interface ServerSource {
    val servers: Flow<List<Server>>
    val activeServer: Flow<Server?>
    suspend fun activeClient(): SubsonicClient
    fun clientFor(server: Server): SubsonicClient

    /** Bağlantıyı doğrular (ping), kaydeder ve aktif yapar; yeni id döner. */
    suspend fun addServer(name: String, baseUrl: String, username: String, password: String): Long
    suspend fun setActive(id: Long)
    suspend fun removeServer(id: Long)
}

interface OfflineModeSource {
    val offlineMode: Flow<Boolean>
}

/** Söz ayarları: internet (LRCLIB) fallback açık mı. */
interface LyricsSettings {
    val internetLyricsEnabled: Flow<Boolean>
}

data class CachedEntry(
    val key: String,
    val json: String,
    val updatedAt: Long,
)

/** Metadata cache deposu (TTL kararını MusicRepository verir). */
interface ApiCacheStore {
    suspend fun get(key: String): CachedEntry?
    suspend fun put(entry: CachedEntry)
    suspend fun deleteByPrefix(prefix: String)
    suspend fun clear()
    suspend fun sizeBytes(): Long
}
