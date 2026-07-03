package com.ozgen.navicloud.data

import com.ozgen.navicloud.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Snapshot of the in-flight download, for progress UI. */
data class ActiveDownload(
    val songId: String,
    val title: String,
    val progress: Float,
    val queued: Int,
    /** "Sadece WiFi'de indir" açık ve ağ metered: kuyruk WiFi'yi bekliyor. */
    val waitingForWifi: Boolean = false,
)

/**
 * İndirme sistemi portu. Android'de dosya tabanlı DownloadRepository;
 * masaüstünde şimdilik indirme yok (boş impl), D5 sonrası gelebilir.
 */
interface DownloadsPort {
    val downloadedIds: Flow<List<String>>
    val active: StateFlow<ActiveDownload?>
    val totalSizeBytes: Flow<Long>
    val totalCount: Flow<Int>
    fun downloadsFor(serverId: Long): Flow<List<Song>>
    suspend fun allDownloadedSongs(): List<Song>
    fun enqueue(songs: List<Song>)
    suspend fun delete(songId: String)
    suspend fun clearAll()
}

/** Arama geçmişi deposu (yalnız sonuç tıklanınca kaydedilir — UI kuralı). */
interface RecentSearchesStore {
    val recents: Flow<List<String>>
    suspend fun record(query: String)
    suspend fun remove(query: String)
    suspend fun clear()
}
