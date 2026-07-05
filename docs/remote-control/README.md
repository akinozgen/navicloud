# NaviCloud Uzaktan Kumanda (Remote Control) — Tasarım & Ticket'lar

> Spotify Connect tarzı **LAN-only**, **tam simetrik** cihazlar-arası kumanda.
> Her NaviCloud örneği aynı anda hem **kontrol edilebilir** (sunucu) hem **kontrol eder** (controller).
> Bu klasör, context limitinden bağımsız kalıcı plandır. Uygulama sırası: RC-0 → RC-6.

## Amaç

Masaüstünde mobili, mobilde masaüstünü canlı kumanda et: cihaz seçici popup'tan bir peer seç,
o cihaz çalar (ses ondan çıkar), sen komut yollar + canlı state görürsün. Bağlantı koparsa
presence yayınıyla cihaz tekrar "bağlanılabilir" olur, kontrol eden taraf yerele düşer.

## Sabitlenen kararlar

| Konu | Karar | Neden |
|---|---|---|
| Kapsam | LAN-only (aynı subnet) | Self-host; internet/relay v1 dışı |
| Simetri | Her peer hem sunucu hem controller | Kullanıcı şartı |
| Eşzamanlılık | Çoklu controller / 1 sunucu (sunucu = SoT) | Kilit yok → disconnect basit |
| Keşif | mDNS/DNS-SD (`_navicloud._tcp`) | Chromecast/AirPlay deseni, framework desteği var |
| Kanal | WS: client **OkHttp WebSocket** (mevcut dep) + sunucu **Ktor server** | Client sıfır dep; sunucu KMP tek dep |
| Güvenlik | PIN eşleştirme (varsayılan) + opsiyonel ortak sır | LAN'da yeterli, kayıtlı cihaz sessiz bağlanır |
| Kimlik/kuyruk çözümü | Song ID'leri aynı Navidrome'da eşleşir; alıcı stream URL'i **kendi** oturumuyla çözer | QueueSync ile aynı varsayım; auth'lu URL cihaza özel |

**Temel varsayım:** İki peer de **aynı Navidrome sunucusuna** bağlı (song ID'leri ortak). Farklı sunucu = ID'ler
çözülmez → **farklı sunucudaki cihazlar listede GİZLENİR** (mDNS TXT'ine serverId hash'i eklenir, keşif filtreler).

## Soru turu kararları (2026-07-04 — kanonik, çelişki olursa bu geçerli)

| Konu | Karar | Etki |
|---|---|---|
| **Bağlanınca kuyruk** | **Akıllı**: alıcı boş/duraksamışsa senin kuyruğun+pozisyonun aktarılır ve çalar; zaten çalıyorsa dokunma, sadece kumanda et | RC-1 connect akışı |
| **Komut kapsamı** | **Standart**: transport + kuyruk düzenleme + shuffle/repeat + ses seviyesi. **Uyku zamanlayıcı/EQ uzaktan YOK** (v1) | RC-0 RcOp seti |
| **Açılış** | **Her zaman "bu cihaz"da başla** — uzak hedef kalıcı değil, otomatik reconnect-on-launch yok | RC-2 manager |
| **Eşleştirme** | ~~Sadece PIN~~ → **RC-7'de GERİ ALINDI**: PIN (varsayılan) + opsiyonel sabit parola (uzaktan). Gerekçe: alıcı ekranını göremeyeceğin uzaktan senaryolar | RC-5 + RC-7 |
| **Devralma (handoff)** | **v1'de YOK** — sadece uzağı sür, "buraya al" sonraki sürüm | kapsam |
| **Cihaz adı** | **Otomatik + düzenlenebilir** (hostname/platform default, ayardan değiştirilir, kalıcı) | RC-2/RC-3 ayar |
| **Alıcı göstergesi** | **Göster** — alıcıda "<cihaz> tarafından kumanda ediliyor" + "kumandayı al" kısayolu | RC-3/RC-4 |
| **Çoklu/zincir** | **Kumanda ederken kilitli** — başkasını süren cihaz "meşgul", 3. cihaz onu kumanda edemez. (Bir alıcıyı N controller sürebilir; ama controller rolündeki cihaz busy) | RC-2/RC-4/RC-5 |
| **Endless (uzakta)** | **Devam etsin** — SET_QUEUE `PlaybackContext` de taşır, alıcı kendi endless mantığıyla sürdürür | RC-0/RC-1 |
| **Manuel IP** | **Kalıcı fallback** — mDNS engelli ağlar (AP isolation) için ayardan IP:port ekleme kalır | RC-3 ayar |
| **Farklı sunucu** | **Gizle** — yalnız aynı Navidrome'daki peer'lar listelenir (serverId TXT filtresi) | RC-2 keşif |

## Neden ucuz: `PlayerController` zaten soyut

UI hiçbir motoru tanımıyor, yalnızca `shared/playback/PlayerApi.kt`'deki `PlayerController` arayüzüyle konuşuyor
(`Media3PlayerController` / `MpvPlayerController` implementasyonları). Uzaktan kumanda = aynı arayüzün 3. implementasyonu
+ bir sunucu sarmalayıcı. **Ekranlar, mini oynatıcı, plak varyantı sıfır değişiklikle uzak cihazı kontrol eder.**

## Mimari

```
UI (değişmez)  →  ActivePlayerController  (PlayerController proxy, TEK sabit ref)
                     ├─ LocalPlayerController   (Media3 / mpv)     ← bu cihazın sesi
                     └─ RemotePlayerController  (OkHttp WS client) ← uzak cihazı sürer
                              │ WS (JSON, kotlinx.serialization)
                              ▼
   Sunucu: RemoteControlServer (Ktor)  →  LOKAL PlayerController'ı sarar,
                                          PlayerUiState → WireState push'lar,
                                          gelen Cmd → lokal player (playerDispatcher'da)
   Keşif:  PeerDiscovery (mDNS)         →  PeerDevice listesi (+ liveness)
   Beyin:  RemoteControlManager         →  device listesi + aktif hedef + reconnect/fallback + pairing
```

**Rol ayrımı (kritik):**
- **Sunucu** yalnızca **lokal** controller'ı sarar (bu cihazın sesi). Advertise + accept (meşgulken hariç, aşağıya bak).
- **ActiveController** yalnızca **bu cihazın UI'ı** içindir (lokal ↔ uzak arası swap eder).
- Bir cihaz başka cihazı sürerken kendi lokal sesi susar VE **"meşgul"** olur → gelen kumandayı `Reject{"busy"}` ile
  reddeder, seçicide soluk/"meşgul" görünür (zincir yok — soru turu kararı).

## Modül dağılımı

Çekirdek platform-bağımsız → **`:shared`** (JVM; Android + desktop tüketir). OkHttp `:shared`'te zaten var
(`MusicRepository`/`LrcLibClient` kullanıyor) → **WS client shared'te**. Ktor sunucu da JVM → çekirdek routing
shared'te; yalnızca *hosting yaşam döngüsü* (Android=`PlaybackService` foreground / desktop=`Main`) ve **mDNS**
(NsdManager vs JmDNS) platforma özel, port arkasında.

```
shared/src/main/kotlin/com/ozgen/navicloud/remote/
  RcProtocol.kt        — @Serializable sealed RcMessage + WireState/WireTrack + RcOp (RC-0)
  RcMapping.kt         — PlayerUiState↔WireState, Cmd→PlayerController dispatch (RC-0)
  RemotePlayerController.kt   — PlayerController impl, WS client (RC-1)
  RemoteControlServer.kt      — Ktor server çekirdeği, lokal player'ı sarar (RC-1)
  RemoteTransport.kt   — WS soket portu (client/server), OkHttp+Ktor impler (RC-1)
  PeerDiscovery.kt     — mDNS portu + PeerDevice modeli (RC-2)
  RemoteControlManager.kt     — orkestratör: devices + target + reconnect (RC-2)
  ActivePlayerController.kt   — local↔remote swap proxy (RC-2)
  PairingStore.kt      — port: bilinen anahtarlar + lokal sır (RC-5)

app/  (Android)  — NsdPeerDiscovery, PlaybackService içinde server host + MulticastLock, DataStorePairingStore, Hilt wiring
desktop/ — JmDnsPeerDiscovery, Main.kt'de server host, FilePairingStore, elle wiring
sharedUi/commonMain — DevicePickerSheet + "cast" ikonu, bağlantı durumu UI (RC-3)
```

## Wire protokolü (özet — detay RC-0)

- `Hello{protocol, deviceId, name, platform, token?}` → `Welcome{...}` | `Reject{reason}` | `PairRequired{nonce}`
- `Cmd{op: RcOp, seq, args}` — op'lar `PlayerController` metotlarıyla 1:1 (PLAY/PAUSE_TOGGLE/NEXT/PREV/SEEK/
  SEEK_INDEX/SEEK_UID/SET_QUEUE/ADD_QUEUE/PLAY_NEXT/REMOVE_UID/MOVE_UID/SHUFFLE/REPEAT/ENDLESS/STOP/VOLUME/REQUEST_STATE)
- `StateMsg{rev, snapshot: WireState}` — bağlanınca tam snapshot, değişimde push
- `Ping`/`Pong` — heartbeat; `SessionInfo{controllers}`

**Pozisyon:** `WireState.positionMs + asOfEpochMs`. Controller `positionMs`'i interpole eder
(`isPlaying` ise `pos + (now - asOfEpochMs)`, `durationMs`'e clamp). Böylece saniyelik state spam'i yok.

**Kuyruk:** `SET_QUEUE` tam `List<Song>` + index + positionMs taşır (Song zaten `@Serializable`, `queue.json`'da
serileşiyor). Alıcı kendi `player.play(songs, startIndex, startPositionMs)`'ini çağırır → stream URL'i lokal çözülür.
**Kapak URL'i wire'da GÖNDERİLMEZ** — controller `song.coverArt`'tan KENDİ oturumuyla türetir (auth'lu URL cihaza özel).
Büyük kuyrukta QueueSync'teki gibi pencere (MAX ~500, current çevresi).

## Ortak mühendislik notları (tüm ticket'lar için)

- **Thread kuralı:** Alıcıda gelen `Cmd`, lokal player'a `playerDispatcher`'da uygulanır — Android `Dispatchers.Main`
  ZORUNLU (Media3 MediaController yalnız ana thread), desktop `Dispatchers.Default`. `QueueSyncManager`'daki desenin aynısı.
- **Sessiz fail:** Tüm ağ/soket çağrıları `runCatching` — kopukluk asla çökme/görünür hata üretmez (kullanıcı şartı,
  QueueSync ile aynı politika).
- **Serileştirme:** Mevcut `Json { ignoreUnknownKeys=true; coerceInputValues=true; explicitNulls=false }` (AppModule/Main).
  `protocol` alanıyla versiyon; bilinmeyen alanlar yok sayılır (ileri uyum).
- **DI:** Android Hilt `AppModule` (bkz. mevcut `provideQueueSyncManager` deseni), desktop `Main.kt` elle
  (bkz. mevcut `QueueSyncManager` kurulumu). `AppContainer`'a (`sharedUi/.../Platform.kt`) `remoteControl: RemoteControlManager?`
  eklenir; sağlanmayan platform `null` bırakır (QueueSync gibi).
- **UI entegrasyonu:** `AppContainer.player` = `ActivePlayerController(local)`. Cast ikonu + `DevicePickerSheet`,
  resume banner'ın durduğu yere (`NaviCloudRoot.kt` MainShell, satır ~106/~300) ve mini/player toggle yanına takılır.

## Ticket index

| # | Dosya | Konu | Bağımlılık |
|---|---|---|---|
| RC-0 ✅ | `RC-0-protocol-core.md` | Protokol DTO'ları + mapping (saf shared, ağsız test) | — |
| RC-1 ✅ | `RC-1-transport-manual-connect.md` | WS client/server + uçtan uca kumanda (manuel-IP UI'ı RC-3'e) | RC-0 |
| RC-2 ✅ | `RC-2-discovery-manager.md` | mDNS keşif + `RemoteControlManager` + `ActivePlayerController` | RC-1 |
| RC-3 ✅ | `RC-3-device-picker-ui.md` | Cihaz seçici popup + cast ikonu + bağlantı durumu | RC-2 |
| RC-4 ✅ | `RC-4-liveness-disconnect.md` | Heartbeat + disconnect/reconnect/fallback + presence | RC-2 |
| RC-5 ✅ | `RC-5-pairing-security.md` | PIN eşleştirme + token + bind güvenliği | RC-1, RC-3 |
| RC-6 🟡 | `RC-6-e2e-verification.md` | LAN E2E + edge-sweep (otonom ✓; fiziksel telefon testi kullanıcıya) | Hepsi |
| RC-7 ✅ | `RC-7-fixed-secret.md` | Sabit parola (uzaktan eşleştirme, PIN'siz) + cihazı unut + self-heal | RC-5 |
| RC-8 ✅ | `RC-8-desktop-single-instance.md` | Masaüstü tek örnek — çift tıkta mevcut pencereyi öne getir | — |

## Bilinen sınırlar / tuzaklar (baştan kabul)

- Router **AP/client isolation** (özellikle guest ağı) peer-to-peer'ı bloklar.
- Android mDNS için `WifiManager.MulticastLock` şart; alıcı sunucu **foreground `PlaybackService`** içinde yaşamalı
  (kill = doğal mDNS goodbye = disconnect).
- İlk sunucu bind'inde Windows Defender firewall promptu.
- Farklı Navidrome sunucularına bağlı peer'lar arası ID'ler çözülmez (v1 dışı).
- Sadece LAN arayüzüne bind — 0.0.0.0 internete açılmaz.
