# RC-0 — Protokol çekirdeği + mapping

**Durum: ✅ TAMAM (2026-07-04).** `shared/.../remote/RcProtocol.kt` + `RcMapping.kt` yazıldı; `PlaybackContext`
`@Serializable` yapıldı. Ağsız selftest (12 mesaj round-trip + polimorfik context + küçük/büyük kuyruk mapping +
pozisyon interpolasyon) geçti; `:shared:compileKotlin` temiz. Selftest harness'ı doğrulama sonrası silindi.

**Hedef:** Ağ/soket olmadan, saf `:shared` içinde wire protokolünü ve iki yönlü mapping'i tanımla.
Bu ticket bittiğinde protokol JVM testiyle round-trip doğrulanabilir; hiçbir platform kodu değişmez.

**Bağımlılık:** yok. **Yeni dep:** yok (kotlinx.serialization zaten var).

## Kapsam

- İçinde: `RcMessage` sealed hiyerarşisi, `WireState`/`WireTrack`, `RcOp`, `PlayerUiState↔WireState` dönüşümü,
  `Cmd → PlayerController` çağrı eşlemesi (dispatch fonksiyonu), pozisyon interpolasyon yardımcısı.
- Dışında: soket, discovery, UI, DI. (RC-1+)

## Oluşturulacak dosyalar

### `shared/src/main/kotlin/com/ozgen/navicloud/remote/RcProtocol.kt`

```kotlin
package com.ozgen.navicloud.remote

import com.ozgen.navicloud.core.model.Song
import kotlinx.serialization.Serializable

const val RC_PROTOCOL_VERSION = 1

@Serializable
sealed interface RcMessage

// --- Handshake ---
@Serializable data class Hello(
    val protocol: Int, val deviceId: String, val name: String,
    val platform: String, val token: String? = null,
) : RcMessage
@Serializable data class Welcome(val deviceId: String, val name: String, val platform: String) : RcMessage
@Serializable data class Reject(val reason: String) : RcMessage          // "protocol" | "auth" | "busy"
@Serializable data class PairRequired(val nonce: String) : RcMessage      // RC-5'te kullanılır

// --- Kontrol (controller → sunucu) ---
enum class RcOp {
    PLAY, PAUSE_TOGGLE, NEXT, PREV, SEEK, SEEK_INDEX, SEEK_UID,
    SET_QUEUE, ADD_QUEUE, PLAY_NEXT, REMOVE_UID, MOVE_UID,
    SHUFFLE, REPEAT, ENDLESS, STOP, VOLUME, REQUEST_STATE,
}
@Serializable data class Cmd(
    val op: RcOp,
    val seq: Long,
    // Argümanlar (op'a göre dolu; kullanılmayan null):
    val songs: List<Song>? = null,      // SET_QUEUE / ADD_QUEUE / PLAY_NEXT
    val startIndex: Int? = null,        // SET_QUEUE
    val positionMs: Long? = null,       // SEEK / SET_QUEUE
    val index: Int? = null,             // SEEK_INDEX
    val uid: String? = null,            // SEEK_UID / REMOVE_UID / MOVE_UID
    val target: Int? = null,            // MOVE_UID
    val volume: Float? = null,          // VOLUME (0f..1f)
    val context: PlaybackContext? = null, // SET_QUEUE — endless "devam etsin" kararı için (alıcı kendi endless'ıyla sürdürür)
    val contextLabel: String? = null,   // SET_QUEUE — "Şuradan çalınıyor: X"
) : RcMessage

// --- State (sunucu → controller) ---
@Serializable data class WireTrack(val uid: String, val song: Song)
@Serializable data class WireState(
    val rev: Long,
    val current: WireTrack?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val shuffle: Boolean,
    val repeat: String,                 // RepeatMode.name
    val queue: List<WireTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val asOfEpochMs: Long,              // positionMs'in geçerli olduğu an
    val contextLabel: String?,
    val volume: Float?,
)
@Serializable data class StateMsg(val snapshot: WireState) : RcMessage

// --- Liveness ---
@Serializable data object Ping : RcMessage
@Serializable data object Pong : RcMessage
@Serializable data class SessionInfo(val controllers: Int) : RcMessage
```

> **Not:** `WireTrack.song` tam `Song` taşır → alıcı `player.play(songs)` ile stream URL'i lokal çözer, controller
> kapak URL'ini `song.coverArt`'tan KENDİ oturumuyla türetir. `artworkUrl` wire'da YOK (auth'lu, cihaza özel).
> `Song`'un `@Serializable` olduğunu doğrula (queue.json'da serileşiyor; değilse ekle).
>
> **Endless kararı ("devam etsin"):** `SET_QUEUE` `PlaybackContext` de taşır → alıcı `play(..., context=...)` ile
> kendi endless mantığını sürdürür. `PlaybackContext` (`PlayerApi.kt`, sealed interface) `@Serializable` + polimorfik
> yapılmalı (her alt tip `@Serializable`; `Json { classDiscriminator }` default yeter). QueueSync bunu YAPMIYORDU —
> uzaktan kumanda bilerek yapıyor.
>
> **Kapsam = "Standart":** RcOp seti bilinçli olarak transport + kuyruk + shuffle/repeat/endless + volume içerir;
> **uyku zamanlayıcı / EQ uzaktan YOK** (v1). Bu tipler protokole eklenmez.

### `shared/src/main/kotlin/com/ozgen/navicloud/remote/RcMapping.kt`

- `fun PlayerUiState.toWire(rev, positionMs, durationMs, contextLabel, volume, now): WireState`
  - Kuyruk penceresi: `queue.size <= MAX_QUEUE (500)` ise tamamı; değilse current çevresinde LOOKBACK=100 geriye + ileri
    (QueueSyncManager ile aynı mantık). `currentIndex` pencereye göre yeniden hesaplanır.
- `fun WireState.toUiState(): PlayerUiState` — controller tarafında (`RemotePlayerController` bunu StateFlow'a koyar).
  `QueueTrack(uid, song, artworkUrl=null)`; artworkUrl controller'da ayrı çözülür (RC-1 notuna bak).
- `fun interpolatedPositionMs(s: WireState, now: Long): Long` =
  `if (s.isPlaying) (s.positionMs + (now - s.asOfEpochMs)).coerceIn(0, s.durationMs) else s.positionMs`
- `suspend fun applyCmd(cmd: Cmd, player: PlayerController, volume: VolumeSink?)` — `when(cmd.op)` her op'u ilgili
  `PlayerController` metoduna map eder (PLAY→`play(songs,startIndex,startPositionMs=positionMs)`, PAUSE_TOGGLE→
  `togglePlayPause()`, SEEK→`seekTo`, SEEK_UID→`seekToUid`, MOVE_UID→`moveQueueItemUidTo`, VOLUME→`volume?.set`, ...).
  **Bu fonksiyon çağrısı `playerDispatcher`'da yapılacak (RC-1 sunucu).** `VolumeSink` = ses için ufak arayüz
  (`var volume: Float`), sunucu tarafında platform sağlar; null olabilir.

## Kabul kriterleri

- [ ] `Json.encodeToString(RcMessage.serializer(), msg)` → `decodeFromString` her mesaj tipi için round-trip eşit.
- [ ] `PlayerUiState.toWire(...).toUiState()` kuyruk/index/current tutarlı (küçük ve >500 kuyruk iki senaryo).
- [ ] `interpolatedPositionMs` çalarken ilerliyor, pause'da sabit, `durationMs`'i aşmıyor.
- [ ] `Song` `@Serializable`.

## Doğrulama

`:shared`'e ufak bir `main()` ya da mevcut selftest deseniyle round-trip + mapping assert'leri (ağsız, saf JVM).
`:shared:compileKotlin` + elle çalıştır. UI/derleme regresyonu yok (yeni paket, kimse import etmiyor).
