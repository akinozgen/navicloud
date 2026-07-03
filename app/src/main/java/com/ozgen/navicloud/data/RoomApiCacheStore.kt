package com.ozgen.navicloud.data

import com.ozgen.navicloud.data.db.ApiCacheDao
import com.ozgen.navicloud.data.db.ApiCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Shared [ApiCacheStore] arayüzünün Android (Room) implementasyonu. */
@Singleton
class RoomApiCacheStore @Inject constructor(
    private val dao: ApiCacheDao,
) : ApiCacheStore {
    override suspend fun get(key: String): CachedEntry? =
        dao.get(key)?.let { CachedEntry(it.key, it.json, it.updatedAt) }

    override suspend fun put(entry: CachedEntry) =
        dao.put(ApiCacheEntity(entry.key, entry.json, entry.updatedAt))

    override suspend fun deleteByPrefix(prefix: String) = dao.deleteByPrefix(prefix)

    override suspend fun clear() = dao.clear()

    override suspend fun sizeBytes(): Long = dao.sizeBytes()
}
