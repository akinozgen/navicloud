# RC-5 — PIN eşleştirme + token + bind güvenliği

**Durum: ✅ TAMAM (2026-07-05).** Yazılanlar: `shared/remote/RcCrypto.kt` (HMAC-SHA256 + randomHex/randomPin +
constant-time eşitlik), `PairingStore.kt` (port + InMemory), protokolde `Challenge{nonce,pairing}` + `Auth{token}`
(+ Welcome.pairKey; Hello.token kaldırıldı). `RemoteControlServer` handshake state-machine (HELLO→AUTH→READY):
Hello sonrası nonce üretir, kayıtlı pairKey varsa `pairing=false` (sessiz HMAC doğrulama), yoksa `pairing=true` +
6 haneli PIN üretip `pairPin` StateFlow'a koyar (alıcı ekranında gösterilir); Auth token'ı constant-time doğrular;
başarı→taze 32-byte pairKey üretip saklar + Welcome'da controller'a yollar; yanlış→`Reject{"auth"}` + rate-limit
(5 hata→30sn cooldown); handshake 60sn timeout; Welcome ÖNCESİ Cmd uygulanmaz. `RemoteControlManager.connectTo`
artık ham bağlantıda tam handshake yapıp SONRA RemotePlayerController'a devrediyor; `pairing=true`'da `pinPrompt`
StateFlow'u + `ConnState.PAIRING` ile UI'dan PIN ister (60sn), token=HMAC(PIN,nonce); Welcome.pairKey'i saklar.
UI (shared NaviCloudRoot): alıcıda "Eşleştirme kodu: XXXXXX" dialog'u (incomingPairPin), controller'da 6-hane PIN
giriş dialog'u (pinPrompt). Platform store: desktop `FilePairingStore` (~/.navicloud/pairing.json), Android
`DataStorePairingStore` (`rc_pair_<id>` anahtarları) — ikisi de loglanmaz, repoda değil.

**Doğrulama:** (1) Loopback selftest (gerçek Ktor/OkHttp/HMAC) 3 senaryo: taze PIN eşleştirme (PIN prompt 1 kez,
iki tarafta AYNI anahtar saklandı), sessiz yeniden bağlanma (PIN İSTENMEDİ), yanlış PIN → FAILED (anahtar
saklanmadı). (2) GERÇEK uygulama (emülatör controller ↔ çalışan masaüstü alıcı, manuel IP): masaüstü PIN üretti
(774357, her bağlantıda TAZE — 777454→774357), emülatörde "Eşleştirme: X'te görünen kodu gir" dialog'u çıktı, doğru
PIN → **bağlandı** (netstat established=1) + masaüstü `pairing.json`'a controllerId→pairKey yazdı; ardından ayrılıp
tekrar bağlanınca **PIN İSTENMEDEN** bağlandı (sessiz, PIN log satırı artmadı). Alıcı PIN'i test için geçici
stdout diagnostiğiyle okundu (masaüstü ekranı görüntülenemediğinden); diagnostik sonra kaldırıldı.

**Not:** Kanal TLS'siz (LAN self-host kararı) — pairKey Welcome'da düz taşınır; kabul edilen sınır. Anahtar kaybı
self-heal: sunucu `pairing=false` auth başarısızsa anahtarı silebilir (ileride; şu an rate-limit + kullanıcı yeniden
eşleştirir). "Ortak sır" modu İPTAL edildi (yalnız PIN).

**Hedef:** LAN'da açık kontrol sunucusu = ağdaki herkes çalmanı sürebilir / ne dinlediğini okur. Eşleşmemiş
controller reddedilsin; eşleşen cihaz sessiz bağlansın.

**Bağımlılık:** RC-1 (handshake), RC-3 (PIN giriş UI'ı).

## Model

- Her cihazın kalıcı `deviceId` (RC-2) + kalıcı **lokal sır** (256-bit random, ilk açılışta üretilir, saklanır).
- Eşleşme = iki cihaz arası paylaşılan `pairKey` (256-bit). `PairingStore`'da `deviceId → pairKey` map.

### `shared/.../remote/PairingStore.kt` (port)
```kotlin
interface PairingStore {
    suspend fun pairKey(deviceId: String): String?      // bilinen peer'ın anahtarı
    suspend fun savePair(deviceId: String, key: String)
    suspend fun localSecret(): String                   // yoksa üretip saklar
}
```
- Android: DataStore impl (`app/`), desktop: `~/.navicloud/pairing.json` (`DesktopPrefs` deseni).

## Handshake akışı (eşleştirme)

1. Controller `connect` → `Hello{deviceId, name, platform, token}`.
   - `token`: bilinen `pairKey` varsa `HMAC-SHA256(pairKey, nonce)`. İlk kez ise `token=null`.
2. Server:
   - **Meşgulse (başkasını kumanda ediyor) → `Reject{"busy"}`** (kilit kararı; RC-1'de de var).
   - `Hello.deviceId` için `pairKey` var + token doğrula → `Welcome`.
   - Aksi → `PairRequired{nonce}` gönder + **ekranda 6 haneli PIN göster** (alıcı cihazda görünür).
3. Controller `PairRequired` alınca → UI'da (RC-3 sheet) **PIN giriş alanı** aç → kullanıcı alıcıdaki PIN'i girer →
   `Hello` tekrar `token = HMAC(PIN, nonce)` ile.
4. Server PIN'i doğrularsa: yeni rastgele `pairKey` üret → controller'a güvenli kanaldan ilet (`Welcome` içinde) →
   **iki taraf da** `savePair(peerDeviceId, pairKey)`. Sonraki bağlantılar sessiz (adım 1'de token geçer).

> Basit ama LAN self-host için yeterli. PIN alıcıda gösterilir, controller'da girilir (Chromecast/TV deseni).

## Ortak-sır modu — İPTAL (soru turu kararı)
Yalnız PIN eşleştirme. "Tek ortak sır" modu v1'de yok (basit tut). Gerekirse ileride eklenir; protokol `token` alanı
zaten HMAC taşıdığı için ileriye dönük kırılma olmaz.

## Bind / ağ güvenliği
- Sunucu **yalnız LAN arayüzü**ne bind (0.0.0.0 internete açık bırakma; NIC IP'sine bind ya da firewall notu).
- Eşleşmemiş `Cmd` KABUL EDİLMEZ (Welcome öncesi gelen komut düşürülür).
- `Reject{"auth"}` sonrası bağlantı kapatılır; controller UI "eşleştirme gerekli/başarısız" gösterir.
- Rate-limit: aynı IP'den art arda başarısız PIN → kısa cooldown (brute-force yavaşlat; PIN 6 hane).

## RC-1/RC-3 üzerine değişiklikler
- `RemoteControlServer` ctor'a `pairing: PairingStore` + `nonce` üretimi + PIN gösterme callback'i
  (`onShowPin: (String) -> Unit` → platform UI'ı gösterir; desktop küçük pencere/bildirim, Android dialog/notification).
- `RemotePlayerController`/`RemoteControlManager.controlDevice` handshake'i pairing'e göre yürütür; `PairRequired` →
  UI'ya PIN iste sinyali (`connState` yeni durumu `PAIRING`).

## Kabul kriterleri
- [ ] İlk bağlantı: alıcıda PIN belirir, controller'da girilince eşleşir; ikinci bağlantı PIN'siz.
- [ ] Yanlış PIN → `Reject`, çalmaya erişim yok, cooldown.
- [ ] Meşgul cihaz (başkasını süren) `Reject{"busy"}` döner.
- [ ] Eşleşmemiş rastgele client hiçbir komut çalıştıramaz.
- [ ] Sır/anahtarlar repoda değil, cihazda kalıcı (DataStore / pairing.json), kaynağa gömülü değil (V2 signoff dersi).

## Tuzaklar
- `pairing.json`/DataStore'a yazılan anahtarlar loglanmamalı. `smtc_cover`/`servers.json` gibi runtime-only.
- HMAC için sabit `nonce` kullanma (replay) — her handshake'te taze nonce.
- PIN'i wire'da düz gönderme yerine `HMAC(PIN, nonce)` — LAN'da yeterli, TLS yok (self-host, ileride ops.).
