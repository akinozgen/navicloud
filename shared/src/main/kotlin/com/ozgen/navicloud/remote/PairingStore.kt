package com.ozgen.navicloud.remote

/**
 * Eşleşmiş cihaz anahtarları deposu (RC-5). deviceId → paylaşılan pairKey (256-bit hex).
 * Android: DataStore, desktop: ~/.navicloud/pairing.json. Anahtarlar LOGLANMAZ; repoda değil.
 *
 * Simetri: hem controller (peer'ın deviceId'siyle) hem alıcı (controller'ın deviceId'siyle) aynı anahtarı saklar.
 */
interface PairingStore {
    suspend fun pairKey(deviceId: String): String?
    suspend fun savePair(deviceId: String, key: String)
    suspend fun clearPair(deviceId: String)
    /** Eşleşmiş tüm deviceId'ler — "cihazı unut" listesi için (RC-7). */
    suspend fun allDeviceIds(): Set<String>
}

/** Sağlanmayan platform için no-op (eşleştirme kapalı → her bağlantı PIN ister ama kaydetmez). */
class InMemoryPairingStore : PairingStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun pairKey(deviceId: String): String? = synchronized(map) { map[deviceId] }
    override suspend fun savePair(deviceId: String, key: String) { synchronized(map) { map[deviceId] = key } }
    override suspend fun clearPair(deviceId: String) { synchronized(map) { map.remove(deviceId) } }
    override suspend fun allDeviceIds(): Set<String> = synchronized(map) { map.keys.toSet() }
}
