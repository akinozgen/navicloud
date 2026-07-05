package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.remote.PairingStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Masaüstü eşleştirme deposu (RC-5) — ~/.navicloud/pairing.json (deviceId → pairKey).
 * Anahtarlar loglanmaz; repoda değil (runtime-only, servers.json gibi).
 */
class FilePairingStore : PairingStore {
    private val file = File(System.getProperty("user.home"), ".navicloud/pairing.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun load(): MutableMap<String, String> = synchronized(lock) {
        runCatching { json.decodeFromString<Map<String, String>>(file.readText()).toMutableMap() }
            .getOrDefault(mutableMapOf())
    }

    private fun save(map: Map<String, String>) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString<Map<String, String>>(map))
        }
    }

    override suspend fun pairKey(deviceId: String): String? = load()[deviceId]

    override suspend fun savePair(deviceId: String, key: String) {
        save(load().apply { put(deviceId, key) })
    }

    override suspend fun clearPair(deviceId: String) {
        save(load().apply { remove(deviceId) })
    }

    override suspend fun allDeviceIds(): Set<String> = load().keys.toSet()
}
