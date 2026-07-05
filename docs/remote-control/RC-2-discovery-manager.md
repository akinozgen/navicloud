# RC-2 — mDNS keşif + RemoteControlManager + ActivePlayerController

**Durum: ✅ TAMAM (2026-07-04).** Yazılanlar: `shared/remote/PeerDiscovery.kt` (PeerDevice[+serverId+busy] +
port + `rcServerId(baseUrl)` SHA-256/12 + TXT map yardımcıları), `ActivePlayerController.kt` (flatMapLatest
swap proxy; sleepTimer HER ZAMAN lokale), `RemoteControlManager.kt` (devices=peers×serverId filtresi;
controlDevice: Hello→Welcome→ilk snapshot bekle [`RemotePlayerController.snapshot`/`rejected` StateFlow'ları]
→ **akıllı aktarım** [uzak boşsa lokal kuyruk+poz+bağlam aktar] → lokali sustur → delegate swap → busy=1
advertise; controlLocal kesintisiz ayrılır [stop GÖNDERMEZ]; onClosed→otomatik Local fallback; açılışta hep
Local). Platform: `desktop/JmDnsPeerDiscovery` (JmDNS 3.6.1; advertise=unregister+register TXT güncelleme;
NIC seçimi DatagramSocket-connect hilesi), `app/remote/NsdPeerDiscovery` (NsdManager + MulticastLock +
SERİ resolve kuyruğu) + manifest ACCESS_WIFI_STATE/CHANGE_WIFI_MULTICAST_STATE. Wiring: `AppContainer.player`
= Active, `AppContainer.remoteControl` eklendi; Android Hilt: PlayerController→Active rebinding, QueueSync +
RC server/manager SOMUT `Media3PlayerController` (lokal) alır; PlaybackService manager start/stop; desktop
Main.kt aynı desen + tray/kapanışta `rcManager.stop()` (mDNS goodbye). Protokole `WireState.endless+context`
eklendi (UI sadakati; sunucu doldurur, uzak controller yansıtır).

**Doğrulama:** (1) Tüm modüller derlendi (Hilt dâhil). (2) Loopback selftest 6 senaryo: farklı-sunucu filtresi,
akıllı aktarım (n=3,pos=5000,ctx=Album aktı + lokal sustu + active remote state gösterdi + busy advertise),
busy kilidi (3. cihaza Reject"busy"), kesintisiz ayrılma (stop yok), aktarım-atlama (uzak doluyken play gitmez),
sunucu ölünce otomatik Local fallback. (3) Gerçek masaüstü uygulaması mDNS'te yayınlandı, ayrı JVM browse ile
çözüldü: `192.168.1.121:46464`, TXT id/v=1/plat/name/srv=1d8a4e251695/busy=0. (4) Emülatör: çökme yok,
NsdService MulticastLock aktif (keşif başladı), UI regresyonsuz (ana sayfa + mini player + QueueSync banner'ı
ActivePlayerController üzerinden ekran görüntüsüyle doğrulandı).

**Sınırlar/notlar:** (a) Emülatör NAT'ı host LAN mDNS'ini göremez → gerçek cihazlar-arası keşif RC-6'da
(telefon+masaüstü). (b) QueueSync resume banner'ı uzak hedefteyken de görünebilir ve Devam LOKALE çalar —
RC-3'te hedef Remote iken banner gizlenecek. (c) Masaüstü ses slider'ı uzak hedefteyken hâlâ LOKAL motoru
kısar — RC-3'te VOLUME cmd köprüsü eklenecek.

**EK UÇTAN UCA DOĞRULAMA (2026-07-05, kullanıcı talebiyle):** İki gerçek-uygulama testi daha koşuldu:
1. **Gerçek zincir (UI hariç birebir UI yolu):** harness JVM'de gerçek `JmDnsPeerDiscovery` + gerçek
   `RemoteControlManager` + `ActivePlayerController` → çalışan masaüstü uygulaması GERÇEK mDNS'ten keşfedildi
   (srv eşleşmesi servers.json hash'iyle), `controlDevice` bağlandı, ACTIVE 198 parçalık gerçek state gösterdi,
   `active.togglePlayPause()` gerçek mpv'de çalmayı FİİLEN başlatıp durdurdu, kesintisiz ayrıldı.
2. **Gerçek cihazlar-arası kuyruk aktarımı:** desktop'un gerçek kuyruğu (198) tel üzerinden emülatörün GERÇEK
   Media3'üne SET_QUEUE edildi — 5'li pencere (idx=1, pos=30sn, ctx=AllSongs yansıdı) + TAM 198'lik kuyruk
   (büyük WS frame'i sorunsuz), Main-dispatcher yolunda çökme yok.

**BULUNAN + DÜZELTİLEN BUG (E2E'nin değeri):** `MpvPlayerController.togglePlayPause` yalnız mpv `pause`
özelliğini çeviriyordu; restore edilen kuyrukta mpv **idle** (dosya yüklü değil) → restart sonrası play tuşu
HİÇBİR ŞEY çalmıyordu (RC'den bağımsız, mevcut masaüstü bug'ı — UI'da da vardı). Düzeltme: idle+kuyruk doluyken
`playAt(index, startMs=pendingResumeMs)` — restore konumundan gerçek başlatma; `restoreQueue` artık
`saved.positionMs`'i `pendingResumeMs`'e taşıyor.

**RC-6'ya kalan tek gerçek boşluk:** telefon+masaüstü fiziksel LAN keşfi (emülatör NAT engeli) + RC-3 UI'ından
kullanıcı akışı.

**Hedef:** Cihazlar birbirini otomatik bulsun; UI, lokal ↔ uzak controller arasında sorunsuz geçsin. Manuel IP kalkar.

**Bağımlılık:** RC-1. **Yeni dep:** JmDNS (desktop). Android `NsdManager` framework (dep yok).

## Oluşturulacak / değişecek dosyalar

### `shared/.../remote/PeerDiscovery.kt` — port + model
```kotlin
data class PeerDevice(
    val deviceId: String,        // kalıcı UUID (bu cihazınki DesktopPrefs / DataStore'da saklanır)
    val name: String,            // "Özgen'in Masaüstü" — otomatik default + ayardan düzenlenebilir (kalıcı)
    val platform: String,        // "desktop" | "android"
    val host: String, val port: Int,
    val serverId: String,        // aktif Navidrome'un hash'i — farklı sunucu FİLTRELENİR (gizlenir)
    val busy: Boolean,           // true = başkasını kumanda ediyor → seçicide soluk/"meşgul", seçilemez
    val lastSeenMs: Long,
)
interface PeerDiscovery {
    val peers: StateFlow<List<PeerDevice>>   // kendisi HARİÇ
    fun startAdvertising(self: PeerDevice)   // servisi yayınla
    fun startBrowsing()                      // diğerlerini keşfet
    fun stop()
}
```
- mDNS servis tipi: `_navicloud._tcp.local.`, instance adı = cihaz adı, port = server `boundPort`.
  TXT: `id`, `v` (protokol), `plat`, `app` (sürüm), `name` (düzenlenebilir ad), **`srv`** (aktif Navidrome serverId hash'i),
  **`busy`** (0/1). `peers`: dedup by `deviceId`; kendini eler; **`srv` bu cihazınkinden farklıysa GİZLE** (soru turu kararı).
  `busy` ise listede kalır ama seçilemez (RC-3). `busy`/ad değişince mDNS servisi güncellenir (yeniden register / TXT update).
- **Cihaz adı:** ilk açılışta `hostname`/platformdan otomatik default üretilir (ör. "NaviCloud Masaüstü"), ayarlardan
  düzenlenebilir + kalıcı saklanır (desktop `DesktopPrefs`, Android DataStore). `self.name` bundan gelir.

### Android — `NsdPeerDiscovery` (`app/`)
- `NsdManager.registerService` (advertise) + `discoverServices`/`resolveService` (browse).
- `WifiManager.MulticastLock` edin (browse boyunca `acquire`, stop'ta `release`) — YOKSA mDNS gelmez.
- `resolveService` seri çalışır (aynı anda tek resolve) — kuyrukla; ServiceInfo → `PeerDevice` (host/port/TXT).
- Yaşam döngüsü: `PlaybackService`'e bağla (advertise server ile aynı ömür).

### Desktop — `JmDnsPeerDiscovery` (`desktop/`)
- `JmDNS.create(InetAddress)`; `registerService(ServiceInfo.create(...))`; `addServiceListener` → resolved event →
  `PeerDevice`. `stop`'ta `unregisterAllServices` + `close` (graceful goodbye).
- LAN arayüzü IP'sini seç (birden çok NIC olabilir → aktif olanı; gerekiyorsa hepsinde yayınla).

### `shared/.../remote/RemoteControlManager.kt` — orkestratör (tek örnek/app)
```kotlin
sealed interface ControlTarget { data object Local : ControlTarget; data class Remote(val deviceId: String) : ControlTarget }
enum class ConnState { IDLE, CONNECTING, CONNECTED, FAILED }

class RemoteControlManager(
    private val local: PlayerController,          // gerçek Media3/mpv
    private val active: ActivePlayerController,    // UI'ın gördüğü proxy
    private val client: RcClient,
    private val discovery: PeerDiscovery,
    private val self: PeerDevice,
    private val scope: CoroutineScope,
    // RC-5: pairing
) {
    val devices: StateFlow<List<PeerDevice>>       // = discovery.peers
    val target: StateFlow<ControlTarget>
    val connState: StateFlow<ConnState>
    fun controlLocal()                             // active.delegate = local; uzak conn kapat
    fun controlDevice(deviceId: String)            // connect → RemotePlayerController → active.delegate = remote
    // RC-4: reconnect/heartbeat/fallback
}
```
- `controlDevice`: peer'ı `devices`'tan çöz → `client.connect(host,port)` → `RemotePlayerController` kur →
  handshake (`Hello`→`Welcome`; `Reject{"busy"}`/`PairRequired` işle) → başarılıysa:
  1. **Lokal çalmayı duraklat** (bu cihazın sesi sussun).
  2. **Akıllı aktarım (soru turu kararı):** İlk `StateMsg`'e bak — uzak cihaz **boş/duraksamış** (currentTrack yok ya da
     `!isPlaying` ve kuyruk boş) ise → SENİN lokal kuyruğunu+pozisyonunu+bağlamını `SET_QUEUE` (context dâhil) ile aktar,
     çalmaya başlat. Uzak cihaz **zaten çalıyorsa** → kuyruğuna DOKUNMA, yalnızca kumanda et.
  3. `active.delegate = remote`, `target = Remote(id)` → **kendini "meşgul" işaretle** (mDNS `busy=1` güncelle; gelen
     kumandaları `Reject{"busy"}` — RC-1 sunucu busy-flag'i buradan okur).
  - Hata → `connState=FAILED`, `target=Local`.
- `controlLocal`: uzak conn kapat, `active.delegate = local`, `target = Local`, `busy=0`. (Lokal otomatik çalmaz.)
- **Açılış (soru turu kararı):** her başlangıçta `target=Local` — son hedef KALICI DEĞİL, launch'ta otomatik reconnect YOK.
- Sunucu rolü bağımsız çalışmaya devam (RC-1'deki `RemoteControlServer` her hâlükârda ayakta; yalnız meşgulken reddeder).

### `shared/.../remote/ActivePlayerController.kt` — swap proxy
- `PlayerController` implement eder; içte `var delegate: PlayerController` (başlangıç = local).
- Her metot `delegate`'e forward. StateFlow'lar için: `delegate`'i `MutableStateFlow<PlayerController>` tut,
  `state = delegateFlow.flatMapLatest { it.state }.stateIn(scope, ..., PlayerUiState())`. Aynısını `contextLabel`,
  `currentContext`, `endless`, `expandRequests`, `streamQuality`, `sleepTimer` için. `positionMs`/`durationMs` getter'lar
  `delegate.positionMs` okur (anlık). Böylece delegate değişince UI reaktif olarak yeni state'e geçer, **tek sabit ref**.

### DI / wiring
- `AppContainer` (`sharedUi/.../Platform.kt`): `val remoteControl: RemoteControlManager? = null` ekle; **`player`
  artık `ActivePlayerController`**. Android Hilt (`AppModule`) + desktop `Main.kt` `ActivePlayerController(localPlayer)`
  kurup `AppContainer.player`'a verir, `RemoteControlManager`'ı da provide edip container'a koyar. `QueueSyncManager`'a
  hangi player verilecek? → **lokal** player (senkron bu cihazın çaldığını sunucuya yazsın; uzak kumanda QueueSync'i
  etkilemez). Doğrula: QueueSync `local`'i almalı, `active`'i değil.

## Kabul kriterleri

- [ ] İki cihaz aynı LAN'da açılınca birbirinin `PeerDevice`'ını ~birkaç sn'de listeler (kendini elemiş).
- [ ] `controlDevice` → UI (mevcut ekranlar/mini) uzak cihazı sürer; lokal ses susar; `controlLocal` → geri döner.
- [ ] Delegate swap'te UI çökmeden yeni state'e geçer (flatMapLatest doğru).
- [ ] QueueSync hâlâ **lokal** çalmayı senkronlar (regresyon yok).
- [ ] Cihaz kapanınca listeden düşer (goodbye/TTL — tam liveness RC-4).

## Tuzaklar
- Android `resolveService` eşzamanlı çağrıda hata verir → tek seferde bir resolve (kuyruk).
- `MulticastLock` unutulursa Android hiç peer görmez — en sık hata.
- `deviceId` KALICI olmalı (ilk açılışta üret, sakla) — her açılışta değişirse liste kirlenir.
- `flatMapLatest` + `stateIn` scope'u manager/uygulama ömrü olmalı (erken iptal = donuk UI).
