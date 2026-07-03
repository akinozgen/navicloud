package com.ozgen.navicloud.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Eski anahtar korunur — geçişte kayıtlı kuyruk kaybolmaz
private val KEY_PERSISTED_QUEUE = stringPreferencesKey("persisted_queue")

/** Shared [QueueStateStore] arayüzünün Android (DataStore) implementasyonu. */
@Singleton
class DataStoreQueueStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : QueueStateStore {
    override suspend fun save(json: String) {
        dataStore.edit { it[KEY_PERSISTED_QUEUE] = json }
    }

    override suspend fun load(): String? = dataStore.data.first()[KEY_PERSISTED_QUEUE]

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY_PERSISTED_QUEUE) }
    }
}
