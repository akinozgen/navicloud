package com.ozgen.navicloud.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
private const val RECENT_LIMIT = 10

/** Shared [RecentSearchesStore] arayüzünün Android (DataStore) implementasyonu. */
@Singleton
class DataStoreRecentSearches @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : RecentSearchesStore {
    override val recents: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[KEY_RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() }.orEmpty() }

    override suspend fun record(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]?.split('\n').orEmpty()
            val updated = (listOf(query) + current.filter { !it.equals(query, ignoreCase = true) })
                .take(RECENT_LIMIT)
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString("\n")
        }
    }

    override suspend fun remove(query: String) {
        dataStore.edit { prefs ->
            val remaining = prefs[KEY_RECENT_SEARCHES]?.split('\n').orEmpty()
                .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            if (remaining.isEmpty()) prefs.remove(KEY_RECENT_SEARCHES)
            else prefs[KEY_RECENT_SEARCHES] = remaining.joinToString("\n")
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY_RECENT_SEARCHES) }
    }
}
