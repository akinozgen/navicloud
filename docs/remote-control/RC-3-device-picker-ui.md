# RC-3 — Cihaz seçici popup + cast ikonu + bağlantı durumu

**Durum: ✅ TAMAM (2026-07-05).** Yazılanlar: `sharedUi/.../components/DevicePickerSheet.kt` (ModalBottomSheet:
"Bu cihaz" + kalem[ad düzenle] + tik; keşfedilen/manuel peer'lar, busy→soluk+"meşgul", CONNECTING→spinner,
FAILED→hata metni; "IP ile ekle" dialog host:port; boş-durum ipucu). PlayerSheet üst barına cast ikonu
(`Cast`/`CastConnected`, Remote iken accent). `NaviCloudRoot`: `RemoteControlBanners` — controller tarafı
"X kumanda ediliyor · Bu cihaza dön", alıcı tarafı "uzaktan kumanda ediliyor · Kumandayı al", FAILED→toast;
`ResumeSyncBanner` uzak hedefteyken GİZLİ. Manager eklemeleri: `currentPeerName`/`remoteVolume`/`controlledBy`
StateFlow'ları, `selfName`+`setSelfName`, `addManualPeer`/`removeManualPeer` (oturum-ömürlü, sunucu filtresi
atlanır), `setRemoteVolume`, `attachReceiver(controllerCount, kick)`, `takeControl`; server `kickControllers()`.
Masaüstü ses slider'ı hedef Remote iken uzak cihaza VOLUME cmd yollar (yoksa lokal mpv). Kimlik/ad kalıcı
(DesktopPrefs / SettingsRepository DataStore).

**Doğrulama — GERÇEK UI uçtan uca (emülatör → çalışan masaüstü, kullanıcı 'bug bulmayayım' uyarısıyla):**
Cast ikonu üst barda ✓ → seçici açıldı ("Bu cihaz" seçili + kalem + boş-durum ipucu + IP ekle) ✓ → "IP ile ekle"
dialog'a `10.0.2.2:46464` girildi ✓ → peer "elle eklendi" olarak listeye düştü ✓ → satıra basınca GERÇEK bağlantı
kuruldu (host `netstat` established=1) ✓ → banner "10.0.2.2:46464 kumanda ediliyor · Bu cihaza dön" + mini bar
MASAÜSTÜNÜN parçasını gösterdi (kendi 'Guitar Soundtrack' değil 'Sleeping Like a Baby/Eddie Noack', kapak
emülatörün oturumuyla yüklendi) ✓ → mini bar'dan NEXT → masaüstü sıradakine geçip ÇALDI, state emülatöre döndü
(tam round-trip) ✓ → "Bu cihaza dön" → bağlantı kapandı (established=0), banner gitti, yerel duruma döndü ✓.

**RC-2/E2E'de bulunan ve bu turda düzeltilen bug** (bkz. RC-2 notu): MpvPlayerController.togglePlayPause idle'da
çalmıyordu — düzeltildi (mevcut masaüstü bug'ıydı, RC'den bağımsız).

**Hedef:** Spotify Connect tarzı cihaz seçici. Mevcut görsel dile uygun; tek baskın liste, gereksiz widget dökümü yok
(memory: [[ytmusic-design-brief]]).

**Bağımlılık:** RC-2. **Platform:** `sharedUi/commonMain` (iki platform ortak).

## Oluşturulacak / değişecek dosyalar

### `sharedUi/.../ui/components/DevicePickerSheet.kt` (yeni)
- Kaynak: `LocalAppContainer.current.remoteControl` (null → hiç gösterme; QueueSync banner deseni gibi).
- İçerik (ModalBottomSheet, mobil; desktop'ta ufak popup/DropdownMenu ya da aynı sheet):
  - Başlık: "Cihaz seç".
  - **▶ Bu cihaz** satırı: `self.name` + "çalıyor/duraklatıldı" alt metni; seçiliyse (target=Local) accent tik.
  - `devices` listesi: her `PeerDevice` → ikon (platform: masaüstü/telefon) + ad + platform/durum alt metni.
    Seçili (target=Remote(id)) → accent + tik. `connState` CONNECTING → satırda spinner; FAILED → kısa "bağlanılamadı".
    **`busy` peer → soluk (disabled) + "meşgul" etiketi, seçilemez** (soru turu kararı). Farklı sunucudakiler zaten
    keşifte gizli (RC-2) → listeye hiç gelmez.
  - **Manuel ekle (kalıcı fallback):** "IP:port ile ekle" alanı — mDNS engelli ağlar için. Girilen adres bir
    `PeerDevice` gibi listeye eklenir (soru turu kararı; RC-1'deki client kullanılır).
  - Boş liste → "Aynı ağda başka NaviCloud cihazı yok — IP ile ekleyebilirsin" ipucu.
- Tık: "Bu cihaz" → `remoteControl.controlLocal()`; peer → `remoteControl.controlDevice(id)`.
- PIN gerekiyorsa (RC-5) → PIN giriş alanı bu sheet'te açılır (RC-5'te doldurulur).

### Cast ikonu (giriş noktası)
- `Icons.Rounded.Cast` / `CastConnected` (target=Remote iken connected). Yerleştir:
  - Tam oynatıcı (PlayerSheet) üst bar — mevcut PiP/mini ikonu (`LocalMiniPlayerToggle`) yanı.
  - Mini oynatıcı (desktop `MiniPlayer.kt` / `MiniVinylWindow.kt`) — küçük ikon (opsiyonel, sığdığı yere).
  - Mobil top bar (MainShell). `remoteControl == null` ise ikon gizli.
- İkon rengi/durumu: Remote iken accent + "→ <peer adı>" ipucu (küçük etiket ya da tooltip).

### Bağlantı durumu göstergesi (iki taraf da)
- **Controller tarafı** (sen uzağı sürüyorsun): Remote iken ince bir bant/etiket "**<peer adı>**'ni kumanda ediyorsun"
  + hızlı "bu cihaza dön" (`controlLocal`) aksiyonu (resume banner'ın olduğu MainShell bölgesi ~satır 300 iyi yer).
  FAILED/kopma → RC-4 ile toast + otomatik Local.
- **Alıcı tarafı** (biri seni sürüyor — soru turu kararı "Göster"): alıcı UI'da ince bir gösterge
  "**<controller adı>** tarafından kumanda ediliyor" + **"kumandayı al"** kısayolu (yerel kontrolü geri alır;
  controller kopar/Local'e düşer). `RemoteControlServer` bağlı-controller olayını UI'a `StateFlow` ile bildirir.

### Ayarlar eklentisi (RC-2 ile)
- **Cihaz adı** düzenleme alanı (otomatik default + değiştir, kalıcı — soru turu kararı).
- **Manuel cihaz (IP:port)** ekleme/silme (mDNS fallback).
- Bu alanlar Android `ServersScreen`/ayarlar + `DesktopSettingsScreen`'e (mevcut desen) eklenir.

## Görsel notlar (brief'e uyum)
- Tek yüzey, sakin; satırlar arası morph/transition yumuşak (ani değişim "çiğ" — memory [[navicloud-project]] UX kuralı).
- Cover/accent dili: seçili cihaz accent'i kapaktan gelen renkle uyumlu olabilir ama zorlama; sade tik + accent yeter.
- Mevcut `SongMenuHost`/ModalBottomSheet desenlerini taklit et; uzun içerik yoksa `skipPartiallyExpanded` gerekmez.

## Kabul kriterleri
- [ ] Cast ikonu yalnız `remoteControl != null` platformda görünür; Remote iken durum belli (renk/etiket).
- [ ] Popup: "Bu cihaz" + bulunan peer'lar; seçim doğru controller'a geçirir; canlı `connState` yansır.
- [ ] Seçim sonrası tüm mevcut UI (kuyruk, seek, play/pause) uzak cihazı sürer — ek değişiklik gerektirmez.
- [ ] `busy` peer soluk + "meşgul", seçilemez; farklı sunucudaki peer listede yok.
- [ ] Manuel IP:port ekleme çalışır (mDNS yokken bile bağlanır).
- [ ] Alıcıda "kumanda ediliyor" göstergesi + "kumandayı al" çalışır.
- [ ] Cihaz adı ayardan değişir, kalıcı, seçicide ve karşı cihazda güncellenir.
- [ ] Boş/tek cihaz durumları anlamlı.

## Doğrulama
Emülatör + desktop aynı LAN. Popup'ı aç → karşı cihaz görünür → seç → mevcut ekranlardan komut → karşıda ses.
Desktop UI doğrulaması preview/log ile (memory [[desktop-ui-verify]]); tam ekran görüntüsü alma.
