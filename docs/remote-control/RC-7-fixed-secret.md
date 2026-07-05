# RC-7 — Sabit parola (uzaktan eşleştirme) + cihazı unut

**Durum: ✅ TAMAM (2026-07-05).** Kullanıcı gerçek kullanımda PIN'in tıkandığı senaryoyu bildirdi: "uzakta olabilirim
(üst kattaki lavabodan alt kattaki PC'de müzik açmak) → alıcının ekranındaki per-connection PIN'i okuyamam." Çözüm:
ayarlardan **sabit parola** (SECRET modu) + **cihazı unut**. RC-5'te iptal edilen "ortak sır" kararı bu gerekçeyle
geri alındı.

## Ne yapıldı
- **Protokol:** `Challenge.pairing:Boolean` → `Challenge.mode: RcAuthMode { KEY, PIN, SECRET }`.
  - KEY: kayıtlı pairKey var → HMAC(pairKey, nonce) (sessiz).
  - PIN: kayıt yok + parola yok → alıcı ekranda 6 haneli PIN gösterir (RC-5 davranışı).
  - **SECRET: alıcıda sabit parola ayarlı → PIN GÖSTERİLMEZ; HMAC(parola, nonce).** Controller aynı parolayı
    biliyorsa hiç ekran görmeden bağlanır (uzaktan kullanım).
- **Sunucu:** `secret: suspend () -> String?` sağlayıcı; mod seçimi KEY→SECRET→PIN önceliğiyle. İlk başarılı PIN/SECRET
  doğrulamasından sonra taze pairKey üretip saklar + Welcome'da yollar → sonraki bağlantılar KEY (sessiz), parola
  değişse de çalışır (unutulana dek).
- **Controller:** SECRET modunda yapılandırılmış parolayı sessiz kullanır; yoksa kullanıcıdan **bir kez** ister
  (PinPrompt.secret=true → "parola" dialog'u, 6-hane kısıtı yok).
- **Cihazı unut:** `RemoteControlManager.forget(deviceId)` + `pairedIds: StateFlow<Set>`; cihaz seçicide eşleşmiş
  peer'larda "unut" (LinkOff) ikonu. `PairingStore.allDeviceIds()` eklendi (desktop dosya / Android DataStore
  `rc_pair_*` anahtar taraması).
- **Self-heal (tek dokunuşta re-pair):** "unut" tek taraflı yapılınca karşı taraf hâlâ eşleşmiş sanır (KEY modu sunar).
  Controller anahtarsızsa **boş token** gönderir → sunucu KEY doğrulaması patlar → **anahtarı siler + aynı bağlantıda
  SECRET/PIN ile yeniden challenge** eder → controller sessizce (parolayla) yeniden eşleşir. Controller handshake
  döngüsü çoklu Challenge'ı işler (maks 2 tur). Böylece unut → tek dokunuşta yeniden bağlanır.
- **Ayar UI:** desktop `DesktopSettings` "Uzaktan kumanda" bölümü + Android `ServersScreen` — parola alanı (boş = PIN).
  Kalıcı: desktop `DesktopPrefs.remoteSecret`, Android `SettingsRepository.rcSecret` (DataStore).

## Doğrulama (loopback selftest, gerçek Ktor/OkHttp/HMAC — 4 senaryo geçti)
- Sabit parola sessiz bağlanma (PIN prompt=0) + eşleşme kaydı.
- İkinci bağlantı KEY moduyla sessiz.
- **Unut → tek dokunuş self-heal re-pair** (controller anahtarsız → sunucu self-heal → SECRET ile sessiz yeniden
  eşleşme, prompt=0).
- Yanlış parola → FAILED (kayıtsız cihaz, uyuşmayan parola).

Gerçek uygulama doğrulaması: RC-5'te PIN akışı emülatör↔masaüstünde kanıtlandı; SECRET modu aynı handshake yolunu
kullanır (yalnız mod farkı) — kullanıcının fiziksel checklist'inde iki cihaza aynı parolayı girip uzaktan sessiz
bağlanma teyit edilecek.

## Not
- Parola LAN'da TLS'siz kanaldan HMAC-challenge ile doğrulanır (düz gönderilmez); pairKey Welcome'da düz — LAN
  self-host kararı. Parola cihazda kalıcı (repoda değil, loglanmaz).
- Wrong-KEY self-heal, deviceId'yi bilen birinin kayıtlı eşleştirmeni silmesine izin verir (mild); ama yine
  SECRET/PIN gerekir → erişim vermez, yalnız yeniden eşleştirme zorlar. LAN'da kabul.
