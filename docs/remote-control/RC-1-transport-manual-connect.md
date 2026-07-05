# RC-1 — WS transport + manuel IP ile uçtan uca kumanda

**Durum: ✅ TAMAM (2026-07-04).** Yazılanlar: `shared/remote/RemoteTransport.kt` (RcConnection/RcClient/RcServer +
`OkHttpRcClient` + `KtorRcServer` [Ktor **3.0.3** CIO, `/rc` endpoint; bind teyidi `resolvedConnectors` ile — hata
fırlar, çağıran port=0 fallback'ine düşer]), `RemotePlayerController.kt` (PlayerController impl; seq'li Cmd gönderir,
StateMsg→state; `playNextByUid`=MOVE_UID(currentIndex+1); positionMs interpolasyonlu), `RemoteControlServer.kt`
(lokal player sarar; Hello→protocol/busy kontrol→Welcome; Cmd'yi `playerDispatcher`'da uygular; state değişimi+1sn
tick push; `controllerCount` StateFlow; `deviceInfo` **suspend provider** — Android DataStore async okur;
`RC_DEFAULT_PORT=46464`). Platform: desktop `Main.kt` (sunucu deps'te start, VolumeSink=mpv engine köprüsü,
deviceId/deviceName `DesktopPrefs`'te [otomatik hostname default]), Android `AppModule.provideRemoteControlServer`
(playerDispatcher=Main, volumeSink=null) + `PlaybackService` onCreate start/onDestroy stop + `SettingsRepository`
rcDeviceId()/rcDeviceName (Build.MODEL default).

**Doğrulama (3 katman, hepsi geçti):**
1. Loopback selftest: handshake (protocol reject/busy reject/welcome) + SET_QUEUE(ctx+pos+artwork resolver) +
   transport/volume komutları + pozisyon interpolasyonu (çalarken ilerler/pause'da sabit) + disconnect (controllerCount).
2. Desktop cross-process: gerçek uygulama + harici probe (`ws://127.0.0.1:46464/rc`) — Welcome (hostname adı),
   gerçek 198 parçalık kuyruk state'i, VOLUME gerçek mpv'ye uygulandı+geri alındı, Ping/Pong, rev tick'leri.
3. Android cross-process: emülatör (navicloud AVD) + `adb forward` — servis içi Ktor bind, gerçek kuyruk state'i,
   **PAUSE_TOGGLE Main-dispatcher yolundan** çalıştı (logcat'te wrong-thread/çökme yok), geri alındı.

**Sapmalar/notlar:** (1) Manuel-IP **UI'ı** bu ticket'ta eklenmedi — ActivePlayerController olmadan bağlantının
takılacağı yer yok; RC-3'te cihaz seçiciyle birlikte gelir (fallback kararı geçerli). Doğrulama UI yerine geçici
probe harness'ıyla yapıldı (silindi). (2) OkHttp WS istemcisinde JVM, bağlantı kapansa da OkHttp thread havuzu
idle-timeout'una dek yaşar — uygulamada sorun değil (süreç zaten yaşıyor), sadece kısa ömürlü CLI'da görünür.
(3) Windows Defender ilk bind'de LAN erişimi için izin isteyebilir — localhost etkilenmez, gerçek LAN testi (RC-6)
öncesi kullanıcı izni onaylamalı.

**Hedef:** Discovery/UI olmadan, **manuel IP:port** girerek bir peer'ı canlı kumanda et. Bu ticket, kanalı ve
`RemotePlayerController`/`RemoteControlServer`'ı kanıtlar — işin kalbi.

**Bağımlılık:** RC-0. **Yeni dep:** Ktor server (sunucu). Client için OkHttp WS (mevcut).

## Kapsam

- İçinde: `RemoteTransport` portu; OkHttp WS client + Ktor WS server implementasyonları; `RemotePlayerController`
  (PlayerController impl); `RemoteControlServer` (lokal player'ı sarar, state push, cmd dispatch); her iki platformda
  sunucuyu ayağa kaldırma + geçici "manuel bağlan" giriş noktası (debug/ayar alanı yeter).
- Dışında: mDNS, cast ikonu/popup, pairing (RC-2/3/5). Bu ticket'ta `token=null` kabul edilir (geçici, güvensiz).

## Bağımlılık ekleme

`gradle/libs.versions.toml`: `ktor = "2.3.x"` (Kotlin 2.1 uyumlu sürüm doğrula) →
`ktor-server-core`, `ktor-server-cio`, `ktor-server-websockets`. `:shared/build.gradle.kts`'e ekle
(OkHttp zaten shared'te). Not: OkHttp `WebSocket` client sınıfı `okhttp3.WebSocket` — ek dep yok.

## Oluşturulacak / değişecek dosyalar

### `shared/.../remote/RemoteTransport.kt`
- İki yönlü soket soyutlaması, JSON çerçeveleri taşır:
```kotlin
interface RcConnection {
    val incoming: Flow<RcMessage>
    suspend fun send(msg: RcMessage)
    fun close()
}
interface RcClient { suspend fun connect(host: String, port: Int): RcConnection }   // OkHttp impl
interface RcServer {                                                                  // Ktor impl
    fun start(port: Int, onConnection: (RcConnection) -> Unit)
    fun stop()
    val boundPort: Int
}
```
- **OkHttpRcClient** (shared): `okhttp3.WebSocket`; `WebSocketListener.onMessage` → `Json.decode` → `incoming` flow;
  `send` → `ws.send(Json.encode(...))`. Yalnız LAN host.
- **KtorRcServer** (shared): CIO engine, `/rc` WS endpoint; her oturum bir `RcConnection`. LAN arayüzüne bind
  (host = `0.0.0.0` LAN'da kabul; RC-5'te sıkılaştırılır). `boundPort` (0 verilirse OS seçer, mDNS'e bunu yayınlarız).

### `shared/.../remote/RemotePlayerController.kt` — `PlayerController` impl
- Ctor: `RcConnection`, `scope`, + `artworkResolver: (Song) -> String?` (controller'ın KENDİ oturumundan kapak URL'i).
- `incoming` collect: `StateMsg` → `_state.value = snapshot.toUiState()` (artworkUrl'ler resolver'dan doldurulur);
  `Pong`/`SessionInfo` işlenir.
- `positionMs` getter: son snapshot + `interpolatedPositionMs(snapshot, now)`; `durationMs` = snapshot.durationMs.
- Her metot ilgili `Cmd`'yi `send` eder (fire; otorite = sunucudan gelen sonraki `StateMsg`). `play()` → `SET_QUEUE`,
  `seekTo`→`SEEK`, vb. `seq` monoton artar.
- StateFlow başlangıcı boş `PlayerUiState()`; bağlanır bağlanmaz `REQUEST_STATE` yollar.
- `contextLabel`/`currentContext`/`endless`/`streamQuality`/`expandRequests`: snapshot'tan türet ya da makul default
  (contextLabel snapshot'ta var; currentContext uzak tarafta anlamlı değil → null; endless snapshot'a eklenebilir ops.).

### `shared/.../remote/RemoteControlServer.kt` — lokal player'ı sarar
- Ctor: `RcServer`, `localPlayer: PlayerController`, `scope`, `playerDispatcher: CoroutineContext`,
  `deviceInfo` (id/name/platform), `volumeSink: VolumeSink?`, (RC-5'te) `pairing`.
- `start(port)`: server başlat; her `RcConnection` için:
  1. İlk mesaj `Hello` bekle → protokol kontrol → **bu cihaz şu an başka bir cihazı kumanda ediyorsa `Reject{"busy"}`**
     (kilit kararı) → (RC-5: token) → `Welcome` gönder (yoksa `Reject`/`PairRequired`). "Meşgul mü" bilgisi
     `RemoteControlManager`'ın `target`'ından okunur (bir busy-flag sağlanır).
  2. Bağlı controller'ı bir set'e ekle; `SessionInfo(controllers=set.size)` yayınla.
  3. `localPlayer.state` (+ periyodik pozisyon tick) → `WireState` → **tüm** bağlı controller'lara `StateMsg` push.
     rev monoton. Pozisyon her push'ta `positionMs`+`asOfEpochMs=now` ile; state değişiminde anında, ayrıca
     düşük frekanslı (örn. 1sn) tick yalnız pozisyon güncellemek için (interpolasyon zaten arada doldurur, tick teyit).
  4. Gelen `Cmd` → `withContext(playerDispatcher) { applyCmd(cmd, localPlayer, volumeSink) }` (thread kuralı!).
  5. `Ping`→`Pong` (RC-4'te tam heartbeat).
- Bağlantı kapanınca set'ten çıkar, `SessionInfo` güncelle.
- **Önemli:** Sunucu her zaman **lokal** controller'ı sarar (bu cihazın sesi), ActiveController'ı DEĞİL.

### Android host — `PlaybackService`
- `RemoteControlServer`'ı foreground `PlaybackService` içinde başlat (mDNS/kill davranışı RC-4). Şimdilik sabit port
  (örn. 0 → OS seçer, log'la). `MulticastLock` RC-2'de. Hilt ile `RcServer`/`RemoteControlServer` provide et
  (bkz. `AppModule` deseni). `localPlayer` = mevcut `PlayerController` (Media3), `playerDispatcher = Dispatchers.Main`.
- **Manuel bağlan:** Ayarlar alanına "IP:port'a bağlan" → `OkHttpRcClient.connect` → `RemotePlayerController`.
  Bu RC-1'de test için, ama **kalıcı fallback** olarak kalır (mDNS engelli ağlar — soru turu kararı); RC-3 bunu
  düzgün ayar UI'ına oturtur.

### Desktop host — `Main.kt`
- `deps` bloğunda `RemoteControlServer(...).start(port)`; `localPlayer = player (mpv)`, `playerDispatcher = Dispatchers.Default`,
  `volumeSink` mevcut `VolumeController`'a köprü. `onCloseRequest`/tray Çıkış'ta `server.stop()`.
- Manuel bağlan: `DesktopSettingsScreen`'e "IP:port" alanı + "Bağlan" → `RemotePlayerController` kur (kalıcı fallback).

## Kabul kriterleri

- [ ] Desktop A ↔ Desktop B (ya da emülatör ↔ desktop) **manuel IP** ile: B'de çal/duraklat/ileri/geri/seek A'dan tetiklenir.
- [ ] A'da canlı state (başlık/kapak/pozisyon) B'nin gerçeğini yansıtır; seek bar interpolasyonla akıcı.
- [ ] `SET_QUEUE`: A, B'ye kuyruk yükletir; B stream URL'i **kendi** Navidrome oturumuyla çözer, çalar.
- [ ] Kapaklar A'da A'nın oturumuyla yüklenir (auth hatası yok).
- [ ] Soket kopunca çökme yok (sessiz fail); RemotePlayerController boş state'e döner.

## Doğrulama

Aynı LAN'da iki örnek. Desktop-desktop en kolay (iki `:desktop:run` farklı `~/.navicloud`? — ya da bir emülatör +
bir desktop). Komut→ses değişimini karşı cihazda gözle; state'i log'la. **Masaüstü tam ekran görüntüsü ALMA** (memory:
[[desktop-ui-verify]]) — log + kullanıcı teyidi.

## Tuzaklar

- Ktor sürümü Kotlin 2.1.0 + coroutines 1.9 ile uyumlu olmalı (2.3.12 civarı test et).
- Android'de Ktor CIO server foreground service içinde: ağ izni (`INTERNET` zaten var), `MulticastLock` RC-2.
- Windows ilk bind'de Defender promptu (kullanıcıya söyle).
