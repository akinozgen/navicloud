package com.ozgen.navicloud.remote

import com.ozgen.navicloud.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/** UI'ın kumanda hedefi. Açılışta HER ZAMAN Local (soru turu kararı — kalıcı değil). */
sealed interface ControlTarget {
    data object Local : ControlTarget
    data class Remote(val deviceId: String) : ControlTarget
}

enum class ConnState { IDLE, CONNECTING, PAIRING, CONNECTED, FAILED }

/**
 * Controller tarafı kimlik istemi (RC-5/RC-7): UI bunu görünce dialog açar.
 * [secret]=true → sabit "parola" istenir (alıcı ekranında PIN yok, uzaktan bağlanma); false → 6 haneli PIN.
 */
class PinPrompt internal constructor(
    val peerName: String,
    val secret: Boolean,
    private val deferred: kotlinx.coroutines.CompletableDeferred<String?>,
) {
    fun submit(value: String) { deferred.complete(value) }
    fun cancel() { deferred.complete(null) }
}

/**
 * Uzaktan kumanda orkestratörü (cihaz başına tek örnek):
 * - [devices]: mDNS'ten gelen, kendisi hariç + AYNI Navidrome'a bağlı peer'lar (farklı sunucu GİZLİ).
 * - [controlDevice]/[controlLocal]: UI hedef geçişi. Bağlanınca **akıllı aktarım** — uzak boşsa
 *   lokal kuyruk+pozisyon+bağlam aktarılıp çalınır; doluysa dokunulmaz, sadece kumanda edilir.
 * - Uzağı sürerken bu cihaz **meşgul**: [isBusy] sunucunun Hello reddine bağlanır, mDNS TXT busy=1 olur.
 * - Bağlantı beklenmedik kopunca otomatik Local'e düşer (tam heartbeat/reconnect RC-4).
 */
class RemoteControlManager(
    private val local: com.ozgen.navicloud.playback.PlayerController,
    private val active: ActivePlayerController,
    private val client: RcClient,
    private val discovery: PeerDiscovery,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineContext,
    private val selfInfo: suspend () -> RcDeviceInfo,
    // Aktif Navidrome kimliği (rcServerId hash'i) — null = sunucu yok (liste boş görünür).
    private val serverIdFlow: kotlinx.coroutines.flow.Flow<String?>,
    private val artworkResolver: suspend (Song) -> String?,
    // Cihaz adı: seçicide görünen düzenlenebilir ad (platform kalıcılaştırır) — RC-3.
    val selfName: StateFlow<String>,
    private val setSelfNameImpl: suspend (String) -> Unit = {},
    // Controller tarafı eşleştirme anahtarları (peer.deviceId → pairKey) — RC-5.
    private val pairing: PairingStore = InMemoryPairingStore(),
    // Sabit uzaktan kumanda parolası (RC-7). Set ise SECRET modunda kullanıcıya sormadan kullanılır.
    private val secret: suspend () -> String? = { null },
) {
    private val _target = MutableStateFlow<ControlTarget>(ControlTarget.Local)
    val target: StateFlow<ControlTarget> = _target.asStateFlow()

    private val _connState = MutableStateFlow(ConnState.IDLE)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    /** Kumanda edilen peer'ın adı — controller tarafı göstergesi ("X kumanda ediliyor"). */
    private val _currentPeerName = MutableStateFlow<String?>(null)
    val currentPeerName: StateFlow<String?> = _currentPeerName.asStateFlow()

    /** PIN/parola istemi (RC-5/RC-7): null=istem yok. UI görünce dialog açar. */
    private val _pinPrompt = MutableStateFlow<PinPrompt?>(null)
    val pinPrompt: StateFlow<PinPrompt?> = _pinPrompt.asStateFlow()

    /** Eşleşmiş cihaz kimlikleri — seçicide "unut" (X) göstermek için (RC-7). */
    private val _pairedIds = MutableStateFlow<Set<String>>(emptySet())
    val pairedIds: StateFlow<Set<String>> = _pairedIds.asStateFlow()

    private fun refreshPaired() {
        scope.launch { _pairedIds.value = runCatching { pairing.allDeviceIds() }.getOrDefault(emptySet()) }
    }

    /** Cihazı unut: kayıtlı anahtarı sil → sonraki bağlantı yeniden eşleşir (PIN/parola). Aktif bağlantı sürer. */
    fun forget(deviceId: String) {
        scope.launch {
            runCatching { pairing.clearPair(deviceId) }
            refreshPaired()
        }
    }

    /** Uzak cihazın ses seviyesi (WireState.volume) — masaüstü slider köprüsü. null = bilinmiyor. */
    private val _remoteVolume = MutableStateFlow<Float?>(null)
    val remoteVolume: StateFlow<Float?> = _remoteVolume.asStateFlow()

    /** BU cihazı kumanda eden controller sayısı — alıcı göstergesi (platform attachReceiver ile bağlar). */
    private val _controlledBy = MutableStateFlow(0)
    val controlledBy: StateFlow<Int> = _controlledBy.asStateFlow()
    private var kickHandler: (() -> Unit)? = null

    /** Alıcıda gösterilecek eşleştirme PIN'i (sunucudan; null=eşleştirme yok) — RC-5. */
    private val _incomingPairPin = MutableStateFlow<String?>(null)
    val incomingPairPin: StateFlow<String?> = _incomingPairPin.asStateFlow()

    /** Elle eklenen peer'lar (mDNS engelli ağlar) — oturum ömürlü, sunucu filtresi ATLANIR (aynı varsayılır). */
    private val manualPeers = MutableStateFlow<List<PeerDevice>>(emptyList())

    /** Seçicide gösterilecek liste: keşif (kendisi hariç + aynı sunucu) + manuel eklenenler. */
    val devices: StateFlow<List<PeerDevice>> =
        combine(discovery.peers, serverIdFlow, manualPeers) { peers, myServer, manual ->
            val discovered = if (myServer == null) emptyList()
            else peers.filter { it.serverId == myServer }
            // Manuel girdi keşifle çakışırsa keşfedilen kazanır (gerçek ad/busy bilgisi onda)
            discovered + manual.filter { m -> discovered.none { it.host == m.host && it.port == m.port } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var remote: RemotePlayerController? = null
    private var remoteConn: RcConnection? = null
    private val switchMutex = Mutex()
    private var boundPort: Int = 0
    private var started = false
    private var advertiseJob: kotlinx.coroutines.Job? = null

    /** Sunucunun Hello reddi için: bu cihaz başka bir cihazı kumanda ediyorsa meşgul. */
    fun isBusy(): Boolean = _target.value is ControlTarget.Remote

    /**
     * Keşfi başlatır: browse + advertise. [port] = RemoteControlServer.boundPort (sunucu start SONRASI çağır).
     * Sunucu/busy/ad değişimlerinde advertise tazelenir.
     */
    fun start(port: Int) {
        if (started) return // idempotent — stop() started'ı sıfırlar, restart mümkün
        started = true
        boundPort = port
        refreshPaired()
        discovery.startBrowsing()
        advertiseJob = scope.launch {
            // Aktif sunucu, hedef (busy) ya da cihaz adı değiştikçe TXT'yi tazele
            combine(serverIdFlow, _target, selfName) { server, tgt, _ -> server to (tgt is ControlTarget.Remote) }
                .collect { (server, busy) -> advertiseSelf(server, busy) }
        }
    }

    /** Alıcı tarafını bağlar: controller sayısı + PIN gösterimi + "kumandayı al" aksiyonu (platform çağırır). */
    fun attachReceiver(controllerCount: StateFlow<Int>, pairPin: StateFlow<String?>, kick: () -> Unit) {
        kickHandler = kick
        scope.launch { controllerCount.collect { _controlledBy.value = it } }
        scope.launch { pairPin.collect { _incomingPairPin.value = it } }
    }

    /** Alıcıda "kumandayı al": bağlı controller'ları düşürür (onlar Local'e döner). */
    fun takeControl() {
        kickHandler?.invoke()
    }

    /** Cihaz adını değiştirir (kalıcı) + mDNS TXT'sini tazeler. */
    fun setSelfName(name: String) {
        scope.launch {
            runCatching { setSelfNameImpl(name.trim()) }
            refreshAdvertise()
        }
    }

    /** Manuel IP:port ekle (mDNS engelli ağlar) — oturum ömürlü; keşfedilenle çakışırsa keşfedilen kazanır. */
    fun addManualPeer(host: String, port: Int) {
        val h = host.trim()
        if (h.isEmpty() || port !in 1..65535) return
        scope.launch {
            val srv = serverIdFlow.first() // aynı Navidrome varsayılır (kullanıcı bilerek ekliyor)
            val peer = PeerDevice(
                deviceId = "manual:$h:$port", name = "$h:$port", platform = "manual",
                host = h, port = port, serverId = srv, busy = false,
                lastSeenMs = System.currentTimeMillis(),
            )
            manualPeers.value = manualPeers.value.filter { it.deviceId != peer.deviceId } + peer
        }
    }

    fun removeManualPeer(deviceId: String) {
        manualPeers.value = manualPeers.value.filter { it.deviceId != deviceId }
    }

    /** Uzak cihazın sesini ayarla (masaüstü slider köprüsü) — hedef Remote değilse no-op. */
    fun setRemoteVolume(volume: Float) {
        val conn = remoteConn ?: return
        scope.launch { runCatching { conn.send(Cmd(RcOp.VOLUME, 0, volume = volume.coerceIn(0f, 1f))) } }
        _remoteVolume.value = volume.coerceIn(0f, 1f) // iyimser; sonraki snapshot doğrular
    }

    /** Ad değişikliği gibi dış güncellemelerden sonra TXT'yi tazeler. */
    fun refreshAdvertise() {
        scope.launch { advertiseSelf(serverIdFlow.first(), isBusy()) }
    }

    private suspend fun advertiseSelf(serverId: String?, busy: Boolean) {
        runCatching {
            val info = selfInfo()
            discovery.advertise(
                PeerDevice(
                    deviceId = info.deviceId,
                    name = info.name,
                    platform = info.platform,
                    host = "", // A kaydından çözülür; TXT taşımaz
                    port = boundPort,
                    serverId = serverId,
                    busy = busy,
                    lastSeenMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** UI hedefini bu cihaza döndürür. Lokal OTOMATİK ÇALMAZ (kullanıcı iradesi). */
    fun controlLocal() {
        scope.launch {
            switchMutex.withLock { teardownRemote() }
        }
    }

    /** Peer'a bağlanıp UI hedefini ona çevirir. Hata = sessiz Local'e dönüş + FAILED. */
    fun controlDevice(deviceId: String) {
        scope.launch {
            switchMutex.withLock {
                val peer = devices.value.find { it.deviceId == deviceId } ?: run {
                    _connState.value = ConnState.FAILED
                    return@withLock
                }
                if (peer.busy) { // seçici zaten engeller; yarışa karşı çifte kontrol
                    _connState.value = ConnState.FAILED
                    return@withLock
                }
                teardownRemote()
                _connState.value = ConnState.CONNECTING
                runCatching { connectTo(peer) }.onFailure {
                    teardownRemote()
                    _connState.value = ConnState.FAILED
                }
            }
        }
    }

    /** Ham bağlantıdan bir sonraki mesajı okur (handshake; RemotePlayerController henüz devralmadı). */
    private suspend fun awaitMsg(conn: RcConnection, timeoutMs: Long): RcMessage =
        withTimeoutOrNull(timeoutMs) { conn.incoming.first() } ?: error("handshake timeout")

    /** Kullanıcıdan PIN (secret=false) ya da sabit parola (secret=true) ister; UI dialog'u pinPrompt'la sürer. */
    private suspend fun promptCredential(peerName: String, secret: Boolean): String? {
        val d = kotlinx.coroutines.CompletableDeferred<String?>()
        _connState.value = ConnState.PAIRING
        _pinPrompt.value = PinPrompt(peerName, secret, d)
        return try {
            withTimeoutOrNull(PIN_ENTRY_TIMEOUT_MS) { d.await() }?.takeIf { it.isNotBlank() }
        } finally {
            _pinPrompt.value = null
            _connState.value = ConnState.CONNECTING
        }
    }

    private suspend fun connectTo(peer: PeerDevice) {
        val conn = client.connect(peer.host, peer.port)
        remoteConn = conn

        // --- Handshake (RC-5): Hello(kimlik) → Challenge → Auth(HMAC) → Welcome/Reject ---
        val info = selfInfo()
        conn.send(Hello(RC_PROTOCOL_VERSION, info.deviceId, info.name, info.platform))
        // Challenge → Auth. KEY modunda yerel anahtar yoksa boş token gönderilir → sunucu self-heal ile bayat
        // anahtarı silip SECRET/PIN ile YENİDEN challenge eder (aynı bağlantıda) → "unut" sonrası tek dokunuş.
        suspend fun tokenFor(ch: Challenge): String = when (ch.mode) {
            RcAuthMode.KEY ->
                pairing.pairKey(peer.deviceId)?.let { RcCrypto.hmac(it, ch.nonce) } ?: "" // boş → sunucu re-challenge
            RcAuthMode.SECRET -> {
                val s = secret()?.takeIf { it.isNotBlank() }
                    ?: promptCredential(peer.name, secret = true) ?: error("parola girilmedi/iptal")
                RcCrypto.hmac(s, ch.nonce)
            }
            RcAuthMode.PIN -> {
                val pin = promptCredential(peer.name, secret = false) ?: error("PIN girilmedi/iptal")
                RcCrypto.hmac(pin, ch.nonce)
            }
        }
        var ch = when (val m = awaitMsg(conn, CONNECT_TIMEOUT_MS)) {
            is Challenge -> m
            is Reject -> error("reddedildi: ${m.reason}")
            else -> error("beklenmeyen handshake mesajı: $m")
        }
        conn.send(Auth(tokenFor(ch)))
        var tries = 0
        loop@ while (true) {
            when (val m = awaitMsg(conn, PIN_ENTRY_TIMEOUT_MS)) {
                is Welcome -> { m.pairKey?.let { pairing.savePair(peer.deviceId, it); refreshPaired() }; break@loop }
                is Challenge -> { // sunucu self-heal → yeni modla tekrar dene
                    if (++tries > 2) error("çok fazla challenge")
                    ch = m
                    conn.send(Auth(tokenFor(ch)))
                }
                is Reject -> error("reddedildi: ${m.reason}")
                else -> error("beklenmeyen handshake mesajı: $m")
            }
        }

        // Handshake bitti → bağlantıyı RemotePlayerController devralır (StateMsg'leri o tüketir).
        val rpc = RemotePlayerController(
            connection = conn,
            scope = scope,
            artworkResolver = artworkResolver,
            onClosed = { onRemoteClosed(peer.deviceId) },
            onKicked = { kickedDeviceId = peer.deviceId },
        )
        remote = rpc

        val snap = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            var s = rpc.snapshot.value
            while (s == null) {
                rpc.rejected.value?.let { error("reddedildi: $it") }
                kotlinx.coroutines.delay(30)
                s = rpc.snapshot.value
            }
            s
        } ?: error("uzak state gelmedi (timeout)")

        // Akıllı aktarım (soru turu kararı): uzak BOŞ ise lokal kuyruğu aktar + çal;
        // uzakta yüklü içerik varsa (çalıyor YA DA duraklatılmış kuyruk) dokunma.
        val localState = local.state.value
        val remoteEmpty = snap.current == null || (!snap.isPlaying && snap.queue.isEmpty())
        if (remoteEmpty && localState.queue.isNotEmpty()) {
            val pos = withContext(playerDispatcher) { local.positionMs }
            rpc.play(
                songs = localState.queue.map { it.song },
                startIndex = localState.currentIndex,
                context = local.currentContext.value,
                contextLabel = local.contextLabel.value,
                startPositionMs = pos,
            )
        }

        // Bu cihazın sesi sussun (uzak çalarken çifte ses olmasın)
        if (localState.isPlaying) withContext(playerDispatcher) { local.togglePlayPause() }

        active.delegate = rpc
        _target.value = ControlTarget.Remote(peer.deviceId)
        _currentPeerName.value = peer.name
        _connState.value = ConnState.CONNECTED
        // Uzak ses seviyesini izle (masaüstü slider köprüsü)
        scope.launch {
            rpc.snapshot.collect { s -> if (remote === rpc) _remoteVolume.value = s?.volume }
        }
    }

    /**
     * Bağlantı karşıdan/ağdan kopunca (RC-4): hedef hâlâ o cihazsa önce OTOMATİK RECONNECT dene
     * (artan backoff), olmazsa Local'e düş. Kullanıcının bilerek controlLocal'i teardownRemote ile
     * remote'u null'lar → buradaki kontrol `remote == null` ise reconnect denemez (kasıtlı ayrılma).
     */
    // Alıcı "kumandayı al" ile bu cihazı düşürdüyse (Bye) burada işaretlenir → onRemoteClosed
    // bunu ağ blibi sanıp reconnect ETMEZ, Local'e düşer. Elle yeniden bağlanma serbest (pencere yok).
    @Volatile private var kickedDeviceId: String? = null

    private fun onRemoteClosed(deviceId: String) {
        if (kickedDeviceId == deviceId) {
            kickedDeviceId = null
            scope.launch {
                switchMutex.withLock { if (isStillTarget(deviceId)) teardownRemote() } // → Local/IDLE, reconnect YOK
            }
            return
        }
        scope.launch { reconnectOrFallback(deviceId) }
    }

    private fun isStillTarget(deviceId: String) =
        (_target.value as? ControlTarget.Remote)?.deviceId == deviceId

    /**
     * Beklenmedik kopmada yeniden bağlanma. **backoff delay'leri kilit DIŞINDA** — böylece kullanıcı bu sırada
     * "Bu cihaza dön"/başka cihaz seçince (controlLocal/controlDevice switchMutex'i hemen alır) reconnect anında
     * iptal olur. Yalnız kısa kontrol + connectTo kilit altında. Hepsi başarısızsa Local + FAILED.
     */
    private suspend fun reconnectOrFallback(deviceId: String) {
        val begin = switchMutex.withLock {
            if (!isStillTarget(deviceId)) return // kasıtlı ayrılma / başka hedef → dokunma
            _connState.value = ConnState.CONNECTING
            runCatching { remote?.close() }
            remote = null
            remoteConn = null
            true
        }
        if (!begin) return
        for (backoff in RECONNECT_BACKOFF_MS) {
            if (!isStillTarget(deviceId)) return
            delay(backoff) // KİLİT DIŞINDA — controlLocal/controlDevice araya girebilir
            val peer = devices.value.find { it.deviceId == deviceId && !it.busy } ?: continue
            val ok = switchMutex.withLock {
                if (!isStillTarget(deviceId)) return // kullanıcı delay sırasında değiştirdi
                runCatching { connectTo(peer) }.isSuccess
            }
            if (ok) return // reconnect'te akıllı-aktarım no-op (uzak dolu, lokal zaten pause)
        }
        switchMutex.withLock {
            if (isStillTarget(deviceId)) {
                teardownRemote()
                _connState.value = ConnState.FAILED
            }
        }
    }

    private fun teardownRemote() {
        runCatching { remote?.close() }
        runCatching { remoteConn?.close() }
        remote = null
        remoteConn = null
        active.delegate = local
        _target.value = ControlTarget.Local
        _currentPeerName.value = null
        _remoteVolume.value = null
        _connState.value = ConnState.IDLE
    }

    fun stop() {
        controlLocal()
        discovery.stop()
        advertiseJob?.cancel()
        advertiseJob = null
        started = false // yeniden start() edilebilsin (Android servis yeniden yaratma)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val PIN_ENTRY_TIMEOUT_MS = 60_000L // kullanıcı PIN girişi için süre
        // Beklenmedik kopmada artan backoff'la yeniden bağlanma denemeleri (RC-4).
        private val RECONNECT_BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L).toList()
    }
}
