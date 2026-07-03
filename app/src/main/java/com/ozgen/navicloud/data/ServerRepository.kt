package com.ozgen.navicloud.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.core.network.SubsonicClient
import com.ozgen.navicloud.core.network.unwrap
import com.ozgen.navicloud.data.db.ServerDao
import com.ozgen.navicloud.data.db.ServerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_ACTIVE_SERVER = longPreferencesKey("active_server_id")

private fun ServerEntity.toModel() = Server(id, name, baseUrl, username, password)

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val dataStore: DataStore<Preferences>,
    private val baseOkHttp: OkHttpClient,
    private val json: Json,
) : ServerSource {
    override val servers: Flow<List<Server>> =
        serverDao.observeAll().map { list -> list.map { it.toModel() } }

    override val activeServer: Flow<Server?> =
        combine(serverDao.observeAll(), dataStore.data) { list, prefs ->
            val activeId = prefs[KEY_ACTIVE_SERVER]
            (list.find { it.id == activeId } ?: list.firstOrNull())?.toModel()
        }

    private val clientCache = mutableMapOf<Long, SubsonicClient>()

    override fun clientFor(server: Server): SubsonicClient = synchronized(clientCache) {
        clientCache.getOrPut(server.id) { SubsonicClient(server, baseOkHttp, json) }
    }

    override suspend fun activeClient(): SubsonicClient {
        val server = activeServer.first()
            ?: throw IllegalStateException("Aktif sunucu yok")
        return clientFor(server)
    }

    /** Validates credentials with a ping before saving. Returns the new server id. */
    override suspend fun addServer(name: String, baseUrl: String, username: String, password: String): Long {
        val candidate = Server(0, name, baseUrl.trimEnd('/'), username, password)
        SubsonicClient(candidate, baseOkHttp, json).api.ping().unwrap()
        val id = serverDao.insert(
            ServerEntity(name = name, baseUrl = baseUrl.trimEnd('/'), username = username, password = password)
        )
        setActive(id)
        return id
    }

    override suspend fun removeServer(id: Long) {
        serverDao.byId(id)?.let { serverDao.delete(it) }
        synchronized(clientCache) { clientCache.remove(id) }
    }

    override suspend fun setActive(id: Long) {
        dataStore.edit { it[KEY_ACTIVE_SERVER] = id }
    }
}
