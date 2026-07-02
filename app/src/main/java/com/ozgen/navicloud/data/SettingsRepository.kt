package com.ozgen.navicloud.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
