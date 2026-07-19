package com.ozgen.navicloud.remote

import com.ozgen.navicloud.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/** Bu cihazın kimliği — handshake + mDNS için. */
data class RcDeviceInfo(val deviceId: String, val name: String, val platform: String)

/** Varsayılan dinleme portu — manuel IP bağlantısında tahmin edilebilir olsun; doluysa OS seçer (port=0). */
const val RC_DEFAULT_PORT = 46464

/**
 * Alıcı tarafı: LOKAL [PlayerController]'ı WS üzerinden expose eder. Gelen [Cmd]'leri [playerDispatcher]'da
 * lokal player'a uygular (Android Main zorunlu — QueueSync kuralı); lokal state'i tüm bağlı controller'lara
 * [StateMsg] olarak push'lar. Her hâlükârda ayakta; [isBusy] (bu cihaz başkasını kumanda ediyorsa) gelen
 * bağlantıyı `Reject{"busy"}` ile geri çevirir (kilit kararı).
 *
 * Güvenlik (RC-5): Hello sonrası HMAC challenge. Kayıtlı pairKey varsa sessiz doğrulama; yoksa alıcıda 6 haneli
 * PIN gösterilir ([pairPin]) ve controller PIN'den token türetir → doğrulanırsa taze pairKey üretilip iki tarafta
 * saklanır. Yanlış PIN rate-limit'lenir. Welcome ÖNCESİ hiçbir Cmd uygulanmaz.
 */
class RemoteControlServer(
    private val server: RcServer,
    private val localPlayer: PlayerController,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineContext,
    // suspend: Android'de kimlik DataStore'dan okunur (async); desktop senkron döner.
    private val deviceInfo: suspend () -> RcDeviceInfo,
    private val volumeSink: VolumeSink? = null,
    private val isBusy: () -> Boolean = { false },
    private val pairing: PairingStore = InMemoryPairingStore(),
    // Sabit "uzaktan kumanda parolası" (RC-7). null/boş → PIN modu. Set → kayıtsız cihaz PIN yerine bu parolayı
    // bilmeli (alıcı ekranını görmeden bağlanma). suspend: DataStore'dan okunur.
    private val secret: suspend () -> String? = { null },
) {
    private val controllers = CopyOnWriteArrayList<RcConnection>()
    private val rev = AtomicLong(0)

    private val _controllerCount = MutableStateFlow(0)
    /** Bağlı controller sayısı — RC-3 "kumanda ediliyor" göstergesi için. */
    val controllerCount: StateFlow<Int> = _controllerCount.asStateFlow()

    /** O an gösterilen eşleştirme PIN'i (null = eşleştirme yok). Alıcı UI bunu dialog'da gösterir (RC-5). */
    private val _pairPin = MutableStateFlow<String?>(null)
    val pairPin: StateFlow<String?> = _pairPin.asStateFlow()

    // Rate limit: art arda yanlış PIN → kısa cooldown (brute-force yavaşlat).
    @Volatile private var failCount = 0
    @Volatile private var cooldownUntil = 0L

    /**
     * "Kumandayı al" (RC-3): tüm bağlı controller'ları düşürür — onlar Local'e döner.
     * Kapatmadan ÖNCE [Bye] gönderir ki controller kopmayı ağ blibi sanıp otomatik reconnect
     * yapmasın (aksi halde kontrol anında geri sıçrar). Kısa gecikme = Bye kapanıştan önce ulaşsın.
     */
    fun kickControllers() {
        controllers.toList().forEach { c ->
            scope.launch {
                runCatching { c.send(Bye) }
                kotlinx.coroutines.delay(120)
                runCatching { c.close() }
            }
        }
    }

    val boundPort: Int get() = server.boundPort

    fun start(port: Int = 0, host: String = "0.0.0.0") {
        server.start(port, host) { conn -> handle(conn) }
    }

    fun stop() = server.stop()

    private enum class Phase { HELLO, AUTH, READY }

    private fun handle(conn: RcConnection) {
        scope.launch {
            var phase = Phase.HELLO
            var nonce = ""
            var mode = RcAuthMode.PIN
            var expectedToken = ""
            var ctrlId = "" // controller deviceId (savePair için)
            var pushJob: Job? = null
            var watchdog: Job? = null
            var lastRxMs = System.currentTimeMillis()
            // Handshake zaman aşımı (PIN girişi için cömert). READY'ye geçemezse düş.
            val handshakeTimeout = launch {
                delay(HANDSHAKE_TIMEOUT_MS)
                if (phase != Phase.READY) conn.close()
            }
            try {
                conn.incoming.collect { msg ->
                    lastRxMs = System.currentTimeMillis()
                    when (phase) {
                        Phase.HELLO -> {
                            if (msg !is Hello) { conn.close(); return@collect }
                            when {
                                msg.protocol != RC_PROTOCOL_VERSION -> reject(conn, "protocol")
                                isBusy() -> reject(conn, "busy")
                                System.currentTimeMillis() < cooldownUntil -> reject(conn, "auth")
                                else -> {
                                    nonce = RcCrypto.randomHex(16)
                                    ctrlId = msg.deviceId
                                    val key = pairing.pairKey(msg.deviceId)
                                    val sec = secret()?.takeIf { it.isNotBlank() }
                                    when {
                                        key != null -> {
                                            mode = RcAuthMode.KEY
                                            expectedToken = RcCrypto.hmac(key, nonce)
                                        }
                                        sec != null -> {
                                            // Sabit parola modu — PIN GÖSTERME (uzaktan bağlanma, RC-7)
                                            mode = RcAuthMode.SECRET
                                            expectedToken = RcCrypto.hmac(sec, nonce)
                                        }
                                        else -> {
                                            mode = RcAuthMode.PIN
                                            val pin = RcCrypto.randomPin()
                                            _pairPin.value = pin // alıcı ekranında göster
                                            expectedToken = RcCrypto.hmac(pin, nonce)
                                        }
                                    }
                                    conn.send(Challenge(nonce, mode))
                                    phase = Phase.AUTH
                                }
                            }
                        }
                        Phase.AUTH -> {
                            if (msg !is Auth) { conn.close(); return@collect }
                            if (!RcCrypto.constantTimeEquals(msg.token, expectedToken)) {
                                // KEY modu başarısız = controller anahtarı unutmuş/bayat → SELF-HEAL: anahtarı sil,
                                // aynı bağlantıda SECRET/PIN ile YENİDEN challenge (tek dokunuşta re-pair).
                                if (mode == RcAuthMode.KEY) {
                                    pairing.clearPair(ctrlId)
                                    nonce = RcCrypto.randomHex(16)
                                    val sec = secret()?.takeIf { it.isNotBlank() }
                                    if (sec != null) {
                                        mode = RcAuthMode.SECRET
                                        expectedToken = RcCrypto.hmac(sec, nonce)
                                    } else {
                                        mode = RcAuthMode.PIN
                                        val pin = RcCrypto.randomPin()
                                        _pairPin.value = pin
                                        expectedToken = RcCrypto.hmac(pin, nonce)
                                    }
                                    conn.send(Challenge(nonce, mode))
                                    return@collect // AUTH fazında kal, yeni Auth bekle
                                }
                                _pairPin.value = null
                                registerFailure()
                                reject(conn, "auth")
                                return@collect
                            }
                            _pairPin.value = null
                            failCount = 0
                            // KEY dışı (PIN/SECRET) ilk doğrulama → taze pairKey üret+sakla+Welcome'da yolla
                            // → sonraki bağlantılar KEY moduyla sessiz (parola değişse de çalışır, unutulana dek).
                            val newKey = if (mode != RcAuthMode.KEY) {
                                RcCrypto.randomHex(32).also { pairing.savePair(ctrlId, it) }
                            } else {
                                null
                            }
                            phase = Phase.READY
                            handshakeTimeout.cancel()
                            controllers.add(conn)
                            _controllerCount.value = controllers.size
                            val info = deviceInfo()
                            conn.send(Welcome(info.deviceId, info.name, info.platform, pairKey = newKey))
                            broadcastSession()
                            pushJob = launch { pushLoop(conn) }
                            watchdog = launch {
                                while (isActive) {
                                    delay(WATCHDOG_TICK_MS)
                                    if (System.currentTimeMillis() - lastRxMs > HEARTBEAT_TIMEOUT_MS) {
                                        conn.close()
                                        break
                                    }
                                }
                            }
                        }
                        Phase.READY -> when {
                            msg is Cmd -> withContext(playerDispatcher) { applyCmd(msg, localPlayer, volumeSink) }
                            msg is Ping -> conn.send(Pong)
                            else -> Unit
                        }
                    }
                }
            } finally {
                handshakeTimeout.cancel()
                pushJob?.cancel()
                watchdog?.cancel()
                // Eşleştirme yarıda kaldıysa PIN'i temizle (bu conn PIN gösteriyorduysa)
                if (phase != Phase.READY) _pairPin.value = null
                if (controllers.remove(conn)) {
                    _controllerCount.value = controllers.size
                    broadcastSession()
                }
            }
        }
    }

    private fun registerFailure() {
        failCount++
        if (failCount >= MAX_FAILS) {
            cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
            failCount = 0
        }
    }

    private suspend fun reject(conn: RcConnection, reason: String) {
        runCatching { conn.send(Reject(reason)) }
        conn.close()
    }

    /** Lokal state değişince + saniyelik tick'te (pozisyon teyidi) snapshot push'lar. */
    private suspend fun pushLoop(conn: RcConnection) {
        val trigger = merge(
            localPlayer.state.map { },
            flow { while (true) { emit(Unit); delay(1000) } },
        )
        trigger.collect {
            val st = localPlayer.state.value
            val pos = withContext(playerDispatcher) { localPlayer.positionMs }
            val dur = withContext(playerDispatcher) { localPlayer.durationMs }
            val label = localPlayer.contextLabel.value
            runCatching {
                conn.send(
                    StateMsg(
                        st.toWire(
                            rev = rev.incrementAndGet(),
                            positionMs = pos,
                            durationMs = dur,
                            contextLabel = label,
                            volume = volumeSink?.volume,
                            nowMs = System.currentTimeMillis(),
                            endless = localPlayer.endless.value,
                            context = localPlayer.currentContext.value,
                        ),
                    ),
                )
            }
        }
    }

    private fun broadcastSession() {
        val n = controllers.size
        controllers.forEach { c -> scope.launch { runCatching { c.send(SessionInfo(n)) } } }
    }

    companion object {
        private const val WATCHDOG_TICK_MS = 1_000L
        private val HEARTBEAT_TIMEOUT_MS = RemotePlayerController.HEARTBEAT_TIMEOUT_MS
        private const val HANDSHAKE_TIMEOUT_MS = 60_000L
        private const val MAX_FAILS = 5
        private const val COOLDOWN_MS = 30_000L
    }
}
