package com.ozgen.navicloud.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android mDNS keşfi (NsdManager). `_navicloud._tcp` hem yayınlar hem tarar.
 * - MulticastLock ZORUNLU (yoksa mDNS paketleri hiç gelmez) — browse boyunca tutulur.
 * - resolveService SERİ çalışmalı (eşzamanlı çağrı FAILURE_ALREADY_ACTIVE) → tek worker kuyruğu.
 * - advertise idempotent: kayıt varsa unregister→(callback)→register (TXT güncelleme yolu).
 * Yaşam döngüsü PlaybackService'e bağlıdır (sunucuyla aynı ömür).
 */
class NsdPeerDiscovery(
    context: Context,
    private val selfDeviceId: String,
) : PeerDiscovery {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    override val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    private val byId = LinkedHashMap<String, PeerDevice>()
    private val nameToId = HashMap<String, String>()
    private val lock = Any()

    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Seri resolve kuyruğu — NsdManager tek eşzamanlı resolve kabul eder
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (found in resolveQueue) {
                val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                @Suppress("DEPRECATION")
                nsd.resolveService(found, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        done.complete(Unit)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        handleResolved(serviceInfo)
                        done.complete(Unit)
                    }
                })
                runCatching { kotlinx.coroutines.withTimeout(8_000) { done.await() } }
            }
        }
    }

    private fun handleResolved(info: NsdServiceInfo) {
        val txt: Map<String, String?> = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8) }
        @Suppress("DEPRECATION")
        val host = info.host?.hostAddress ?: return
        val peer = peerFromTxt(txt, host, info.port, System.currentTimeMillis()) ?: return
        if (peer.deviceId == selfDeviceId) return
        synchronized(lock) {
            byId[peer.deviceId] = peer
            nameToId[info.serviceName] = peer.deviceId
            _peers.value = byId.values.toList()
        }
    }

    override fun advertise(self: PeerDevice) {
        scope.launch {
            runCatching {
                // Önce eski kaydı düşür (TXT güncellemenin tek yolu); callback beklemek şart değil —
                // NsdManager register'ı sıraya alır, ad çakışırsa kendisi yeniden adlandırır.
                registrationListener?.let { runCatching { nsd.unregisterService(it) } }
                val info = NsdServiceInfo().apply {
                    serviceType = RC_SERVICE_TYPE
                    serviceName = "${self.name} [${self.deviceId.take(6)}]"
                    port = self.port
                    self.toTxt().forEach { (k, v) -> setAttribute(k, v) }
                }
                val listener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                registrationListener = listener
                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            }
        }
    }

    override fun startBrowsing() {
        if (discoveryListener != null) return
        // MulticastLock: Android varsayılanda multicast paketlerini filtreler — mDNS için açılmalı
        runCatching {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("navicloud-rc").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    nameToId.remove(serviceInfo.serviceName)?.let { byId.remove(it) }
                    _peers.value = byId.values.toList()
                }
            }
        }
        discoveryListener = listener
        runCatching { nsd.discoverServices(RC_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    override fun stop() {
        runCatching { discoveryListener?.let { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        runCatching { registrationListener?.let { nsd.unregisterService(it) } } // mDNS goodbye
        registrationListener = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        synchronized(lock) {
            byId.clear()
            nameToId.clear()
            _peers.value = emptyList()
        }
    }
}
