package com.ozgen.navicloud.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Akış kalitesi: RAW = transcode yok; diğerleri sunucu tarafında MP3'e düşürür. */
enum class StreamQuality(val kbps: Int?, val label: String) {
    RAW(null, "Orijinal (transcode yok)"),
    HIGH(320, "Yüksek • 320 kbps"),
    MEDIUM(192, "Normal • 192 kbps"),
    LOW(128, "Düşük • 128 kbps"),
}

private val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
private val KEY_STREAM_CACHE_MAX_MB = intPreferencesKey("stream_cache_max_mb")
private val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
private val KEY_PREFETCH_ENABLED = booleanPreferencesKey("prefetch_enabled")
private val KEY_PREFETCH_WIFI_ONLY = booleanPreferencesKey("prefetch_wifi_only")

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val streamQuality: Flow<StreamQuality> = dataStore.data.map { prefs ->
        prefs[KEY_STREAM_QUALITY]?.let { runCatching { StreamQuality.valueOf(it) }.getOrNull() }
            ?: StreamQuality.RAW
    }

    suspend fun setStreamQuality(quality: StreamQuality) {
        dataStore.edit { it[KEY_STREAM_QUALITY] = quality.name }
    }

    val offlineMode: Flow<Boolean> = dataStore.data.map { it[KEY_OFFLINE_MODE] ?: false }

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[KEY_OFFLINE_MODE] = enabled }
    }

    /** Akış cache'i üst sınırı (MB). */
    val streamCacheMaxMb: Flow<Int> = dataStore.data.map { it[KEY_STREAM_CACHE_MAX_MB] ?: 512 }

    suspend fun setStreamCacheMaxMb(mb: Int) {
        dataStore.edit { it[KEY_STREAM_CACHE_MAX_MB] = mb }
    }

    /** Kalıcı indirmeler yalnızca WiFi'de (metered ağda kuyruk bekler). */
    val downloadWifiOnly: Flow<Boolean> = dataStore.data.map { it[KEY_DOWNLOAD_WIFI_ONLY] ?: true }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_DOWNLOAD_WIFI_ONLY] = enabled }
    }

    /** Sıradaki şarkıların ilk kısmını önden akış cache'ine ısıt. */
    val prefetchEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_PREFETCH_ENABLED] ?: true }

    suspend fun setPrefetchEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PREFETCH_ENABLED] = enabled }
    }

    val prefetchWifiOnly: Flow<Boolean> = dataStore.data.map { it[KEY_PREFETCH_WIFI_ONLY] ?: true }

    suspend fun setPrefetchWifiOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_PREFETCH_WIFI_ONLY] = enabled }
    }
}
