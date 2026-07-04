package com.ozgen.navicloud.playback

import kotlinx.serialization.Serializable

/**
 * Kuyruk senkronu için ufak kalıcı durum deposu (QueueStateStore ile aynı desen).
 * Android'de DataStore, masaüstünde dosya implemente eder.
 */
interface QueueSyncStateStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

/**
 * En son sunucuyla uzlaşılan kuyruk imzası. serverId değişince imza geçersiz sayılır
 * (yeni sunucuda temiz başlar → yanlış "başka cihazda kaldın" göstermez).
 */
@Serializable
data class QueueSyncState(
    val serverId: String? = null,
    val lastSyncedSignature: String? = null,
)
