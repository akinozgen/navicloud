# RC-4 — Heartbeat + disconnect/reconnect/fallback + presence

**Durum: ✅ TAMAM (2026-07-05).** Yazılanlar: `RemotePlayerController` app-level heartbeat — her incoming mesajda
`lastRxMs`, `PING_INTERVAL_MS=2000`'de bir `Ping`, `HEARTBEAT_TIMEOUT_MS=5000` boyunca hiç mesaj gelmezse
`connection.close()` (→ incoming biter → onClosed); `close()` artık `closed` bayrağıyla döngüleri de durduruyor.
`RemoteControlServer` her controller için watchdog (1sn tick; controller'dan `HEARTBEAT_TIMEOUT_MS` sessizse conn
kapatılır → finally controller'ı düşürür + `SessionInfo` broadcast). `RemoteControlManager.onRemoteClosed` artık
**reconnectOrFallback**: hedef hâlâ o cihazsa artan backoff'la (500/1000/2000ms) yeniden bağlanır (peer görünür +
!busy iken), hepsi olmazsa `teardownRemote`→Local + `FAILED` (RemoteControlBanners toast). Kasıtlı `controlLocal`
teardown yaptığı için reconnect denemez. Reconnect'te `connectTo` akıllı-aktarımı no-op (uzak dolu) + lokal zaten
pause → temiz devam. Presence: mDNS goodbye (RC-2 stop) + serviceLost zaten wire'lı; ekstra TTL filtresi
EKLENMEDİ (canlı ama yeniden çözülmemiş peer'ı yanlışlıkla gizlememek için — mDNS lost/expiry yeterli).

**Doğrulama:** (1) Loopback selftest (gerçek Ktor/OkHttp) 3 senaryo: sunucu aniden ölür → controller ~heartbeat
içinde kopar (CONNECTING'e geçer); sunucu geri gelir → **otomatik reconnect → CONNECTED**; kalıcı kopma (peer
listeden de düşer) → tüm denemeler başarısız → **Local fallback (FAILED)**. (2) Gerçek uygulama: emülatör → çalışan
masaüstü (manuel IP 10.0.2.2), bağlıyken masaüstü süreci **aniden öldürüldü** (crash simülasyonu, `netstat`
established 1→0) → emülatör temiz biçimde **Local'e döndü** (banner gitti, mini bar yerel parça, "kumanda ediliyor"da
TAKILMADI), logcat'te **çökme/ANR yok**. Reconnect state-machine'i loopback'te (sunucu geri geldiğinde) authoritative
kanıtlandı; gerçek-kill senaryosunda sunucu geri gelmediği için beklendiği gibi Local'e düştü.

**Not/sınır:** Saf "sessiz paket düşmesi" (RST'siz) heartbeat-timeout yolu paket filtreleme gerektirdiğinden gerçek
cihazda ayrıca test edilmedi; süreç-kill RST yolunu + reconnect + fallback'i kapsıyor, timeout yolu loopback'te
sunucu-stop ile örneklendi. Fiziksel iki-cihaz Wi-Fi kesme testi RC-6'da.

**Hedef:** Kopmalar hızlı ve temiz yönetilsin. Kullanıcının şartı: **disconnect broadcast olur, cihaz tekrar
"bağlanılabilir" olur, kontrol eden taraf yerele düşer.** İki katman: mDNS presence (yavaş) + WS heartbeat (hızlı).

**Bağımlılık:** RC-2 (RC-1 sunucu/istemci üstüne kurar).

## İki katmanlı liveness

| Katman | Mekanizma | Hız | Kapsam |
|---|---|---|---|
| Presence | mDNS announce / goodbye / TTL-expiry | saniyeler–on saniye | "cihaz ağda var mı" |
| Session | WS `Ping`/`Pong` heartbeat | ~2sn ping / 5sn timeout | "aktif bağlantı canlı mı" |

## Heartbeat protokolü
- **Client (`RemotePlayerController`):** her `PING_INTERVAL` (2sn) `Ping` yolla; son `Pong`/herhangi mesajdan bu yana
  `HEARTBEAT_TIMEOUT` (5sn) geçtiyse → bağlantı ölü say → `close()` + `RemoteControlManager`'a bildir.
- **Server (`RemoteControlServer`):** `Ping`→`Pong`; ayrıca kendi tarafında controller'dan `HEARTBEAT_TIMEOUT` mesaj
  gelmezse o oturumu düşür + `SessionInfo` güncelle.

## Disconnect senaryoları → sonuç

| Olay | Algı | Sonuç |
|---|---|---|
| Controller düzgün ayrılır (`controlLocal`/kapanış) | WS close frame | Sunucu oturumu düşürür, **çalmaya devam**, kalan controller'lara `SessionInfo` |
| Ağ/çökme (ungraceful) | heartbeat timeout | İki taraf teardown; controller `connState=FAILED` → **otomatik Local fallback** + toast "bağlantı koptu" |
| Sunucu offline (kapanış) | mDNS goodbye | Controller'lar `devices`'tan düşürür; o cihaza bağlıysa Local'e döner |
| Sunucu offline (çökme) | mDNS TTL + heartbeat | Aynı; heartbeat daha hızlı yakalar |
| Cihaz geri gelir | mDNS re-announce | Tekrar "bağlanılabilir"; controller `RECONNECT_TRIES` kez otomatik dener, olmazsa Local kalır |

## RemoteControlManager eklemeleri
- `connState: StateFlow<ConnState>` canlı güncellenir (CONNECTING/CONNECTED/FAILED).
- **Reconnect:** Remote hedefteyken beklenmedik kopma → aynı `deviceId` hâlâ `devices`'ta ise `RECONNECT_TRIES` (örn. 3),
  artan backoff (0.5s→1s→2s) ile yeniden `connect`. Başarı → CONNECTED (state kaldığı yerden gelir, sunucu SoT).
  Başarısız → `controlLocal()` + toast.
- **Fallback:** her başarısız/kopuk durumda `active.delegate = local`, `target=Local`. Lokal **otomatik çalmaz**
  (kullanıcı iradesi). Toast: "<peer> bağlantısı koptu — bu cihaza dönüldü".
- **Presence temizliği:** `devices` içinden `lastSeenMs` çok eskiyeni (TTL) ele — mDNS goodbye kaçırılırsa emniyet.

## Presence broadcast (mDNS tarafı)
- Android alıcı: `PlaybackService` kill/onDestroy → `NsdManager.unregisterService` (goodbye). Kill edilemezse TTL düşer.
- Desktop: `Main` kapanış/`onCloseRequest` tam çıkışta → JmDNS `unregisterAllServices()`+`close()`. Tray'e küçülme
  (çalışmaya devam) → advertise SÜRER (hâlâ bağlanılabilir — doğru davranış).
- `SessionInfo{controllers}` her bağlan/kopla güncellenir; UI istenirse "N cihaz kumanda ediyor" gösterebilir (ops.).

## Sabitler (companion)
```
PING_INTERVAL_MS = 2_000
HEARTBEAT_TIMEOUT_MS = 5_000
RECONNECT_TRIES = 3
RECONNECT_BACKOFF_MS = [500, 1_000, 2_000]
PEER_TTL_MS = 15_000      // lastSeen bundan eskiyse listeden düş
```

## Kabul kriterleri
- [ ] Wi-Fi'yi kesince controller ≤5sn'de kopmayı yakalar, Local'e düşer, toast gösterir (çökme yok).
- [ ] Kısa kopmada (kablo/AP micro-drop) otomatik reconnect başarılı, kullanıcı fark etmeden state döner.
- [ ] Alıcıyı kapatınca cihaz ≤TTL içinde diğerlerinin listesinden düşer; geri açınca yeniden görünür.
- [ ] Controller ayrılınca sunucu çalmaya devam eder (kesinti yok).
- [ ] Çoklu controller: biri koparken diğeri etkilenmez.

## Tuzaklar
- Ktor WS ping/pong'unun kendi `pingPeriod`'u var — kendi app-level `Ping`/`Pong`'umuzu onunla karıştırma (biz
  uygulama katmanında ölçüyoruz; Ktor'unki taşıma katmanı, ikisi de açık olabilir).
- Reconnect fırtınası: cihaz gerçekten gittiyse `devices`'ta yoksa deneme (mDNS'e güven).
- Android Doze/uyku: alıcı foreground service ile ayakta; controller arka plandayken heartbeat coroutine'i yaşam
  döngüsüne bağla (uygulama arkada + ekran kapalı senaryosunu RC-6'da test et).
