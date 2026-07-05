package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.remote.PeerDevice
import com.ozgen.navicloud.remote.PeerDiscovery
import com.ozgen.navicloud.remote.RC_SERVICE_TYPE
import com.ozgen.navicloud.remote.peerFromTxt
import com.ozgen.navicloud.remote.toTxt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Masaüstü mDNS keşfi (JmDNS). `_navicloud._tcp.local.` tipinde hem yayınlar hem tarar.
 * Kendi deviceId'sini eler; deviceId ile dedup. Tüm JmDNS çağrıları IO'da (create/register bloklayıcı).
 * advertise idempotent: kayıt varsa unregister+register (TXT güncelleme yolu — busy/ad/sunucu değişimi).
 */
class JmDnsPeerDiscovery(
    private val selfDeviceId: String,
) : PeerDiscovery {

    private val serviceType = "$RC_SERVICE_TYPE.local."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    // deviceId → peer; mDNS instance adı → deviceId (serviceRemoved yalnız adı verir)
    private val byId = LinkedHashMap<String, PeerDevice>()
    private val nameToId = HashMap<String, String>()

    @Volatile private var jmdns: JmDNS? = null
    @Volatile private var registered: ServiceInfo? = null
    @Volatile private var browsing = false
    private val lock = Any()

    /** Varsayılan rotadaki NIC'in adresi — paket gönderilmez (connect yalnız hedef atar). */
    private fun localAddress(): InetAddress =
        runCatching {
            DatagramSocket().use { s ->
                s.connect(InetAddress.getByName("8.8.8.8"), 53)
                s.localAddress
            }
        }.getOrNull()?.takeIf { !it.isAnyLocalAddress } ?: InetAddress.getLocalHost()

    private fun ensureJmdns(): JmDNS = synchronized(lock) {
        jmdns ?: JmDNS.create(localAddress()).also { jmdns = it }
    }

    override fun advertise(self: PeerDevice) {
        scope.launch {
            runCatching {
                val dns = ensureJmdns()
                synchronized(lock) {
                    registered?.let { runCatching { dns.unregisterService(it) } }
                    // Instance adı benzersiz olmalı: ad + kısa id (aynı hostname'li iki cihaz çakışmasın)
                    val instance = "${self.name} [${self.deviceId.take(6)}]"
                    val info = ServiceInfo.create(serviceType, instance, self.port, 0, 0, self.toTxt())
                    dns.registerService(info)
                    registered = info
                }
            }
        }
    }

    override fun startBrowsing() {
        scope.launch {
            runCatching {
                val dns = ensureJmdns()
                synchronized(lock) {
                    if (browsing) return@launch
                    browsing = true
                }
                dns.addServiceListener(serviceType, object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        // Çözümü iste; sonuç serviceResolved'a düşer
                        scope.launch { runCatching { dns.requestServiceInfo(event.type, event.name, 3000) } }
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info ?: return
                        val txt = buildMap<String, String?> {
                            val keys = info.propertyNames
                            while (keys.hasMoreElements()) {
                                val k = keys.nextElement()
                                put(k, info.getPropertyString(k))
                            }
                        }
                        val host = info.inet4Addresses.firstOrNull()?.hostAddress
                            ?: info.inetAddresses.firstOrNull()?.hostAddress ?: return
                        val peer = peerFromTxt(txt, host, info.port, System.currentTimeMillis()) ?: return
                        if (peer.deviceId == selfDeviceId) return
                        synchronized(lock) {
                            byId[peer.deviceId] = peer
                            nameToId[event.name] = peer.deviceId
                            _peers.value = byId.values.toList()
                        }
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        synchronized(lock) {
                            nameToId.remove(event.name)?.let { byId.remove(it) }
                            _peers.value = byId.values.toList()
                        }
                    }
                })
            }
        }
    }

    override fun stop() {
        scope.launch {
            runCatching {
                synchronized(lock) {
                    browsing = false
                    registered = null
                    byId.clear()
                    nameToId.clear()
                    _peers.value = emptyList()
                }
                jmdns?.unregisterAllServices() // mDNS goodbye — presence düşer
                jmdns?.close()
                jmdns = null
            }
        }
    }
}
