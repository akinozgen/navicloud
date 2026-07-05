package com.ozgen.navicloud.remote

import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

/**
 * mDNS'te görünen bir NaviCloud örneği. `serverId` aktif Navidrome'un hash'i — FARKLI sunucudaki
 * cihazlar seçicide GİZLENİR (soru turu kararı); `busy` = o cihaz başka birini kumanda ediyor →
 * soluk/"meşgul", seçilemez.
 */
data class PeerDevice(
    val deviceId: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int,
    val serverId: String?,
    val busy: Boolean,
    val lastSeenMs: Long,
)

/**
 * mDNS keşif portu (`_navicloud._tcp`). Android: NsdManager (+MulticastLock), desktop: JmDNS.
 * [peers] kendisi HARİÇ, deviceId ile dedup'lu canlı liste. [advertise] idempotent: kayıtlıysa
 * TXT güncellenir (unregister+register) — busy/ad/sunucu değişimlerinde yeniden çağrılır.
 */
interface PeerDiscovery {
    val peers: StateFlow<List<PeerDevice>>
    fun advertise(self: PeerDevice)
    fun startBrowsing()
    fun stop()
}

/** mDNS servis tipi (nokta içermeyen çekirdek ad — platform impl'leri kendi biçimine çevirir). */
const val RC_SERVICE_TYPE = "_navicloud._tcp"

/** TXT anahtarları. */
object RcTxt {
    const val ID = "id"
    const val VERSION = "v"
    const val PLATFORM = "plat"
    const val NAME = "name"
    const val SERVER = "srv"
    const val BUSY = "busy"
}

/**
 * Aktif Navidrome sunucusunun kimlik hash'i — baseUrl normalize edilip kısaltılmış SHA-256.
 * Aynı sunucuya bağlı iki cihaz aynı değeri üretir; şifre/kullanıcı dahil değildir (sızıntı yok).
 */
fun rcServerId(baseUrl: String): String {
    val norm = baseUrl.trim().trimEnd('/').lowercase()
    val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(12)
}

/** TXT map'inden PeerDevice üretir (host/port çözümden gelir); zorunlu alan eksikse null. */
fun peerFromTxt(txt: Map<String, String?>, host: String, port: Int, nowMs: Long): PeerDevice? {
    val id = txt[RcTxt.ID]?.takeIf { it.isNotBlank() } ?: return null
    val version = txt[RcTxt.VERSION]?.toIntOrNull() ?: return null
    if (version != RC_PROTOCOL_VERSION) return null
    return PeerDevice(
        deviceId = id,
        name = txt[RcTxt.NAME]?.takeIf { it.isNotBlank() } ?: "NaviCloud",
        platform = txt[RcTxt.PLATFORM] ?: "?",
        host = host,
        port = port,
        serverId = txt[RcTxt.SERVER]?.takeIf { it.isNotBlank() },
        busy = txt[RcTxt.BUSY] == "1",
        lastSeenMs = nowMs,
    )
}

/** PeerDevice → TXT map'i (advertise tarafı). */
fun PeerDevice.toTxt(): Map<String, String> = buildMap {
    put(RcTxt.ID, deviceId)
    put(RcTxt.VERSION, RC_PROTOCOL_VERSION.toString())
    put(RcTxt.PLATFORM, platform)
    put(RcTxt.NAME, name)
    serverId?.let { put(RcTxt.SERVER, it) }
    put(RcTxt.BUSY, if (busy) "1" else "0")
}
