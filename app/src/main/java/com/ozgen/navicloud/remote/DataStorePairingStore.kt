package com.ozgen.navicloud.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android eşleştirme deposu (RC-5) — DataStore'da `rc_pair_<deviceId>` anahtarlarında pairKey saklar.
 * Anahtarlar loglanmaz; kaynağa gömülü değil (runtime-only).
 */
@Singleton
class DataStorePairingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PairingStore {
    private fun key(deviceId: String) = stringPreferencesKey("rc_pair_$deviceId")

    override suspend fun pairKey(deviceId: String): String? =
        dataStore.data.first()[key(deviceId)]

    override suspend fun savePair(deviceId: String, key: String) {
        dataStore.edit { it[key(deviceId)] = key }
    }

    override suspend fun clearPair(deviceId: String) {
        dataStore.edit { it.remove(key(deviceId)) }
    }

    override suspend fun allDeviceIds(): Set<String> =
        dataStore.data.first().asMap().keys
            .map { it.name }
            .filter { it.startsWith("rc_pair_") }
            .map { it.removePrefix("rc_pair_") }
            .toSet()
}
