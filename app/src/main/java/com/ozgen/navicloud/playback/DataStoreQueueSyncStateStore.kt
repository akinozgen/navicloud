package com.ozgen.navicloud.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_QUEUE_SYNC_STATE = stringPreferencesKey("queue_sync_state")

/** Shared [QueueSyncStateStore] arayüzünün Android (DataStore) implementasyonu. */
@Singleton
class DataStoreQueueSyncStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : QueueSyncStateStore {
    override suspend fun save(json: String) {
        dataStore.edit { it[KEY_QUEUE_SYNC_STATE] = json }
    }

    override suspend fun load(): String? = dataStore.data.first()[KEY_QUEUE_SYNC_STATE]
}
