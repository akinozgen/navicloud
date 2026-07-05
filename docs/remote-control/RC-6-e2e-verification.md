# RC-6 — Uçtan uca doğrulama (LAN, iki yönlü, kopar-toparla)

**Durum: 🟡 KISMİ (2026-07-05).** Otonom yapılabilen her şey yapıldı + doğrulandı; **fiziksel telefon↔masaüstü
gerçek-Wi-Fi mDNS oturumu KULLANICIYA bırakıldı** (test kuralı: kullanıcının telefonuna dokunma; emülatör NAT'ı
host LAN mDNS'ini göremez). Aşağıdaki manuel checklist ile koşulacak.

## Otonom yapılan + geçen
- **Release/R8**: `:app:assembleRelease` (minify+shrink) GEÇTİ — Ktor/serialization keep kuralları eklendi
  (`proguard-rules.pro`: `-keep io.ktor.**`, `kotlinx.coroutines.**`, `-dontwarn slf4j/ktor`). 8.9MB APK.
- **Tüm modül derlemeleri** (shared/desktop/app debug+release) temiz.
- Önceki ticket'ların gerçek-uygulama doğrulamaları RC-6 senaryolarının çoğunu zaten kapsıyor: gerçek mDNS
  advertise+browse (RC-2), gerçek discovery→connect→control (RC-2 harness), gerçek cross-device SET_QUEUE→Media3
  (RC-2), emülatör↔masaüstü control+fallback (RC-3/4), gerçek kill→Local (RC-4), gerçek PIN pairing+sessiz
  reconnect (RC-5). Tek eksik gerçek-fiziksel kombinasyon: telefon↔masaüstü aynı Wi-Fi'da mDNS keşfi.

## EDGE-CASE SWEEP (2026-07-05, kullanıcı "bariz kaçırdığımız noktalar vardır" dedi) — BULUNAN + DÜZELTİLEN
- **BUG-A (fonksiyonel, Android):** `RemoteControlManager.stop()` `started`'ı sıfırlamıyordu + `KtorRcServer.start`
  idempotent değildi → `PlaybackService` yeniden yaratılınca (onDestroy→stop, onCreate→start) manager.start() no-op
  olup keşif/yayın ÖLÜYORDU, ayrıca ikinci Ktor server eskiyi kapatmadan bind edip port çakışması/sızıntı yapıyordu
  (server = Hilt singleton, restart'ta aynı instance). **Fix:** stop() → advertiseJob.cancel()+started=false;
  KtorRcServer.start başında stop() (idempotent).
- **BUG-B (UX):** reconnect `switchMutex`'i tüm backoff (3.5sn) boyunca tutuyordu → "Bu cihaza dön"/başka cihaz
  seçme reconnect bitene dek bloke. **Fix:** backoff `delay`'leri kilit DIŞINA alındı; yalnız kısa kontrol+connectTo
  kilit altında → controlLocal artık **anında (0ms)** kesiyor.
- Loopback selftest 3 senaryo (KtorRcServer idempotency / manager start-stop-start browse=2 / controlLocal
  reconnect'i keser) GEÇTİ.

## Kabul edilen sınırlar (v1, bug değil — dokümante)
- **PIN/banner yalnız ANA pencerede:** masaüstü tepsiye küçültülmüş/mini moddayken ya da Android'de app arka
  plandayken alıcı PIN dialog'u görünmez → eşleştirme için app'i öne getir. (İleride: masaüstü PIN gelince pencereyi
  öne getir / Android bildirim.)
- **SMTC (masaüstü) / medya bildirimi (Android) LOKAL player'ı yansıtır**, sürülen uzak cihazı değil — kozmetik.
- **QueueSync SoT:** uzak sürerken lokal pause olur (bir kez lokal kuyruğu push'lar); gerçek çalan = alıcı, onun
  QueueSync'i doğru. Zararsız.
- **Manuel peer serverId** ekleme anında sabitlenir; aktif Navidrome değişirse bayatlar.
- **Kanal TLS'siz** (pairKey Welcome'da düz) — LAN self-host kararı.

## MANUEL TEST CHECKLIST (kullanıcı — fiziksel telefon + masaüstü, aynı Wi-Fi)
Telefona `NaviCloud-release.apk` kur, masaüstünde app'i aç (ana pencere açık). İkisi de AYNI Navidrome'a bağlı olsun.
- [ ] **Keşif:** Telefonda tam oynatıcı → cast ikonu → seçicide masaüstü "NaviCloud • <host>" birkaç sn'de görünür
      (ve tersi: masaüstünde telefon). Görünmüyorsa: router AP/client-isolation? Aynı SSID mi? → "IP ile ekle" fallback.
- [ ] **PIN eşleştirme:** İlk seçimde masaüstünde PIN belirir, telefonda gir → bağlanır.
- [ ] **İki yönlü kumanda:** Telefondan masaüstünü çal/duraklat/ileri/geri/seek/kuyruk; sonra masaüstünden telefonu.
- [ ] **Akıllı aktarım:** Boş cihaza bağlanınca senin kuyruğun aktarılır; dolu cihaza bağlanınca dokunulmaz.
- [ ] **Sessiz reconnect:** Ayrıl + tekrar bağlan → PIN İSTENMEZ.
- [ ] **Kopma:** Kumanda ederken alıcıyı kapat / Wi-Fi kes → controller ≤5sn'de "bağlantı koptu" + bu cihaza döner.
- [ ] **Kilit:** A telefonu sürerken 3. cihaz A'yı "meşgul" (soluk) görür, seçemez.
- [ ] **Ses (masaüstü→telefon yok / telefon→masaüstü):** uzak masaüstü sürerken ses slider'ı masaüstü sesini kısar.
- [ ] **Regresyon:** Tek cihaz normal çalma/kuyruk/mini/plak/QueueSync etkilenmemiş.

**Hedef:** Tüm zincirin gerçek LAN'da, iki yönlü ve kopma senaryolarıyla çalıştığını kanıtla. Ticketlanır
(memory [[feedback-workflow]] / [[test-workflow]] desenine uygun — emülatörde test, kullanıcının telefonuna dokunma).

**Bağımlılık:** RC-0…RC-5.

## Ortam
- Emülatör (`navicloud` AVD) + masaüstü, **aynı Wi-Fi/subnet**. Emülatör ağı NAT'lı olduğundan gerçek LAN mDNS'i
  görmeyebilir → gerekiyorsa gerçek cihaz yerine **iki masaüstü** ya da emülatör host köprüsü kullan; senaryoyu
  ortamın izin verdiği en gerçekçi ikiliyle çalıştır. (Emülatör mDNS kısıtı = bilinen risk, RC-2 tuzağı.)
- İki peer de **aynı Navidrome** sunucusuna bağlı (ID varsayımı).
- **Masaüstü tam ekran görüntüsü ALMA** (memory [[desktop-ui-verify]]) — log + kullanıcı teyidi ile doğrula.

## Senaryolar

### S1 — Keşif & seçim
- [ ] Her iki cihaz açık → cast popup'ta karşı cihaz birkaç sn'de görünür, kendini elemiş.
- [ ] Cihaz adları/platform ikonları doğru.

### S2 — İki yönlü kumanda
- [ ] Masaüstü → mobili kumanda: çal/duraklat/ileri/geri/seek/kuyruk değiştir → mobilde ses + state.
- [ ] Mobil → masaüstünü kumanda: aynı komut seti → masaüstünde ses + state.
- [ ] Canlı state (başlık/kapak/pozisyon) doğru; seek bar interpolasyonla akıcı; kapaklar kumanda eden cihazın
      oturumuyla yükleniyor (auth hatası yok).

### S3 — Eşleştirme
- [ ] İlk bağlantıda alıcıda PIN belirir, controller'da girilince eşleşir; ikinci bağlantı PIN'siz.
- [ ] Yanlış PIN reddedilir; eşleşmemiş cihaz komut çalıştıramaz.

### S4 — Disconnect / reconnect / fallback
- [ ] Controller Wi-Fi kes → ≤5sn kopma yakalanır → Local'e düşer + toast; çökme yok.
- [ ] Kısa micro-drop → otomatik reconnect, state döner.
- [ ] Alıcıyı kapat → cihaz ≤TTL'de listeden düşer; geri aç → yeniden görünür + tekrar bağlanılır.
- [ ] Controller ayrılırken alıcı çalmaya devam eder.

### S5 — Çoklu controller (simetri)
- [ ] İki controller aynı alıcıyı sürer → ikisi de canlı state görür, ikisi de komut verebilir; biri koparken diğeri
      etkilenmez; `SessionInfo` sayısı doğru.

### S6 — Regresyon
- [ ] `remoteControl` YOK gibi davranış (tek cihaz): mevcut çalma/kuyruk/mini/plak/QueueSync aynen çalışır.
- [ ] QueueSync hâlâ **lokal** çalmayı senkronlar; uzak kumanda onu bozmuyor.
- [ ] Release (R8/masaüstü paket) build derlenir; Ktor/JmDNS proguard/packaging sorunları yok
      (R8 kurallarını kontrol et — Ktor reflection, kotlinx.serialization zaten yapılandırılı).

### S7 — Soru turu kararlarına özel
- [ ] **Akıllı aktarım:** alıcı boşken bağlan → senin kuyruğun aktarılıp çalar; alıcı çalarken bağlan → kuyruğu bozulmaz, sadece kumanda.
- [ ] **Kilit/meşgul:** A, B'yi kumanda ederken C'nin seçicisinde A "meşgul" soluk görünür, seçilemez (`Reject{"busy"}`).
- [ ] **Endless (uzakta):** aktarılan kuyruk bitince alıcı kendi endless'ıyla devam eder (bağlam taşındı).
- [ ] **Manuel IP:** mDNS'siz (elle IP:port) bağlanma çalışır.
- [ ] **Farklı sunucu:** başka Navidrome'a bağlı peer seçicide GÖRÜNMEZ.
- [ ] **Alıcı göstergesi:** biri kumanda edince alıcıda "kumanda ediliyor" + "kumandayı al" çalışır.
- [ ] **Cihaz adı:** ayardan değişir, karşı seçicide güncellenir, kalıcı.
- [ ] **Açılış:** yeniden başlatınca uzağa otomatik bağlanmaz, "bu cihaz"da başlar.
- [ ] **Kapsam:** uyku zamanlayıcı/EQ uzaktan DEĞİŞTİRİLEMEZ (v1 kasıtlı); transport+kuyruk+shuffle/repeat+ses çalışır.

## Çıktılar
- Madde madde sonuç raporu (memory [[feedback-workflow]]).
- Gerekirse `NaviCloud-Setup.exe` + `NaviCloud-release.apk` Desktop'a (mevcut `scripts/build-release.ps1` deseni).
- README/ticket'lardaki sapmalar işlenir (gerçek davranış plandan farklıysa dosya güncellenir).

## Notlar
- Bu, "premium" hissi için son cila turu: gecikme, titreme, kapak yüklenmesi, popup animasyonu brief'e uygun mu
  (memory [[ytmusic-design-brief]]).
- CI (release.yml) yeni dep'lerle hâlâ derleniyor mu? (Ktor/JmDNS Windows+Android matris) — kullanıcı manuel izler
  (gh/token yok).
