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

private val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
private val KEY_STREAM_CACHE_MAX_MB = intPreferencesKey("stream_cache_max_mb")
private val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
private val KEY_PREFETCH_ENABLED = booleanPreferencesKey("prefetch_enabled")
private val KEY_PREFETCH_WIFI_ONLY = booleanPreferencesKey("prefetch_wifi_only")
private val KEY_INTERNET_LYRICS = booleanPreferencesKey("internet_lyrics")
private val KEY_RC_DEVICE_ID = stringPreferencesKey("rc_device_id")
private val KEY_RC_DEVICE_NAME = stringPreferencesKey("rc_device_name")
private val KEY_RC_SECRET = stringPreferencesKey("rc_secret")
private val KEY_LANGUAGE = stringPreferencesKey("app_language")

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OfflineModeSource, LyricsSettings {
    val streamQuality: Flow<StreamQuality> = dataStore.data.map { prefs ->
        prefs[KEY_STREAM_QUALITY]?.let { runCatching { StreamQuality.valueOf(it) }.getOrNull() }
            ?: StreamQuality.RAW
    }

    suspend fun setStreamQuality(quality: StreamQuality) {
        dataStore.edit { it[KEY_STREAM_QUALITY] = quality.name }
    }

    override val offlineMode: Flow<Boolean> = dataStore.data.map { it[KEY_OFFLINE_MODE] ?: false }

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

    /** Sunucuda söz yoksa LRCLIB'ten internet fallback (varsayılan açık). */
    override val internetLyricsEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_INTERNET_LYRICS] ?: true }

    suspend fun setInternetLyrics(enabled: Boolean) {
        dataStore.edit { it[KEY_INTERNET_LYRICS] = enabled }
    }

    // --- Uzaktan kumanda kimliği (RC-1/RC-2) ---

    /** Kalıcı cihaz kimliği — ilk çağrıda üretilip saklanır (handshake + mDNS dedup). */
    suspend fun rcDeviceId(): String {
        var id: String? = null
        dataStore.edit { prefs ->
            id = prefs[KEY_RC_DEVICE_ID] ?: java.util.UUID.randomUUID().toString().also {
                prefs[KEY_RC_DEVICE_ID] = it
            }
        }
        return id!!
    }

    /** Cihaz seçicide görünen ad — otomatik default (model), ayardan düzenlenebilir. */
    val rcDeviceName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_RC_DEVICE_NAME]?.takeIf { it.isNotBlank() }
            ?: "NaviCloud • ${android.os.Build.MODEL}"
    }

    suspend fun setRcDeviceName(name: String) {
        dataStore.edit { prefs ->
            val v = name.trim()
            if (v.isBlank()) prefs.remove(KEY_RC_DEVICE_NAME) else prefs[KEY_RC_DEVICE_NAME] = v
        }
    }

    /** Sabit uzaktan kumanda parolası (RC-7); boş = PIN modu. */
    val rcSecret: Flow<String> = dataStore.data.map { it[KEY_RC_SECRET].orEmpty() }

    suspend fun setRcSecret(secret: String) {
        dataStore.edit { prefs ->
            val v = secret.trim()
            if (v.isBlank()) prefs.remove(KEY_RC_SECRET) else prefs[KEY_RC_SECRET] = v
        }
    }

    /** Uygulama dili (SYSTEM/TURKISH/ENGLISH); varsayılan sistem locale'i. */
    val language: Flow<com.ozgen.navicloud.i18n.AppLanguage> =
        dataStore.data.map { com.ozgen.navicloud.i18n.appLanguageOf(it[KEY_LANGUAGE]) }

    suspend fun setLanguage(language: com.ozgen.navicloud.i18n.AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.name }
    }
}
