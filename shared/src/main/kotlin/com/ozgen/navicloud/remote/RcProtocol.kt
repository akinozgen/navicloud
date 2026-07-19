package com.ozgen.navicloud.remote

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.playback.PlaybackContext
import kotlinx.serialization.Serializable

/**
 * Uzaktan kumanda tel protokolü (RC-0). LAN-only, simetrik peer; controller → sunucu komut,
 * sunucu → controller state. Tüm mesajlar kotlinx.serialization ile JSON.
 *
 * Sürüm alanı [Hello.protocol] ile taşınır; bilinmeyen alanlar Json(ignoreUnknownKeys) ile yok sayılır
 * (ileri uyum). Detay + kararlar: docs/remote-control/.
 */
const val RC_PROTOCOL_VERSION = 1

@Serializable
sealed interface RcMessage

// --- Handshake ---

/** Kimlik tanıtımı (RC-5'te token yok — doğrulama Challenge/Auth ile ayrı adımda). */
@Serializable
data class Hello(
    val protocol: Int,
    val deviceId: String,
    val name: String,
    val platform: String,
) : RcMessage

/**
 * Doğrulama modu (RC-5/RC-7). Sunucu Challenge'da hangisini beklediğini söyler:
 * - KEY: kayıtlı pairKey var → HMAC(pairKey, nonce) (sessiz).
 * - PIN: kayıt yok, sabit parola da yok → sunucu ekranda 6 haneli PIN gösterir; HMAC(PIN, nonce).
 * - SECRET: sunucuda sabit "uzaktan kumanda parolası" ayarlı → HMAC(parola, nonce); PIN GÖSTERİLMEZ
 *   (controller aynı parolayı biliyorsa hiç ekran görmeden bağlanır — uzaktan kullanım için RC-7).
 */
enum class RcAuthMode { KEY, PIN, SECRET }

/** Sunucu → controller, Hello sonrası. [nonce] = taze challenge; [mode] = beklenen doğrulama. */
@Serializable
data class Challenge(val nonce: String, val mode: RcAuthMode) : RcMessage

/** Controller → sunucu (RC-5). token = HMAC(pairKey|PIN, nonce). */
@Serializable
data class Auth(val token: String) : RcMessage

/** [pairKey] yalnız TAZE eşleştirmede dolu (PIN doğrulandı) → controller bunu saklar; sonraki bağlantılar sessiz. */
@Serializable
data class Welcome(
    val deviceId: String,
    val name: String,
    val platform: String,
    val pairKey: String? = null,
) : RcMessage

/** reason: "protocol" | "auth" | "busy" (kumanda-ederken-kilitli kararı). */
@Serializable
data class Reject(val reason: String) : RcMessage

// --- Kontrol (controller → sunucu) ---

/**
 * Kapsam = "Standart" (soru turu kararı): transport + kuyruk + shuffle/repeat/endless + ses.
 * Uyku zamanlayıcı / EQ uzaktan YOK (v1) → protokolde yer almaz.
 */
enum class RcOp {
    PLAY, PAUSE_TOGGLE, NEXT, PREV, SEEK, SEEK_INDEX, SEEK_UID,
    SET_QUEUE, ADD_QUEUE, PLAY_NEXT, REMOVE_UID, MOVE_UID,
    SHUFFLE, REPEAT, ENDLESS, STOP, VOLUME, REQUEST_STATE,
}

@Serializable
data class Cmd(
    val op: RcOp,
    /** Monoton artan istek sırası (idempotensi/log için; sunucu SoT). */
    val seq: Long = 0L,
    // Argümanlar — op'a göre dolu, kullanılmayan null:
    val songs: List<Song>? = null,          // SET_QUEUE / PLAY / ADD_QUEUE / PLAY_NEXT
    val startIndex: Int? = null,            // SET_QUEUE / PLAY
    val positionMs: Long? = null,           // SEEK / SET_QUEUE / PLAY
    val index: Int? = null,                 // SEEK_INDEX
    val uid: String? = null,                // SEEK_UID / REMOVE_UID / MOVE_UID
    val target: Int? = null,                // MOVE_UID
    val volume: Float? = null,              // VOLUME (0f..1f)
    /** SET_QUEUE/PLAY — endless "devam etsin" kararı: bağlam aktarılır, alıcı kendi endless'ıyla sürdürür. */
    val context: PlaybackContext? = null,
    val contextLabel: String? = null,       // "Şuradan çalınıyor: X"
) : RcMessage

// --- State (sunucu → controller) ---

@Serializable
data class WireTrack(val uid: String, val song: Song)

/**
 * Oynatıcı durumunun tel görüntüsü. [positionMs] + [asOfEpochMs] ile controller pozisyonu interpole eder
 * (bkz. [interpolatedPositionMs]) → saniyelik state spam'i gerekmez. Kapak URL'i taşınmaz: controller
 * `song.coverArt`'tan KENDİ oturumuyla türetir (auth'lu URL cihaza özel).
 */
@Serializable
data class WireState(
    val rev: Long,
    val current: WireTrack?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val shuffle: Boolean,
    val repeat: String,                     // RepeatMode.name
    val queue: List<WireTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val asOfEpochMs: Long,
    val contextLabel: String?,
    val volume: Float?,
    /** Alıcının endless anahtarı — controller UI'ı doğru toggle durumu göstersin. */
    val endless: Boolean = false,
    /** Alıcının çalma bağlamı — koleksiyon sayfaları "çalıyor" göstergesini uzakta da doğru çizsin. */
    val context: PlaybackContext? = null,
)

@Serializable
data class StateMsg(val snapshot: WireState) : RcMessage

// --- Liveness (RC-4) ---

@Serializable
data object Ping : RcMessage

@Serializable
data object Pong : RcMessage

/** Sunucuya bağlı controller sayısı — çoklu kumanda göstergesi için (bir alıcıyı N controller sürebilir). */
@Serializable
data class SessionInfo(val controllers: Int) : RcMessage

/**
 * Alıcı bu controller'ı BİLEREK düşürdü ("kumandayı al"). Controller bunu alınca kopmayı
 * ağ blibi sanıp OTOMATİK reconnect YAPMAZ — Local'e düşer. Kullanıcı isterse elle yeniden bağlanır.
 */
@Serializable
data object Bye : RcMessage
