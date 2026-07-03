<p align="center">
  <img src="docs/icon.png" width="128" alt="NaviCloud" />
</p>

<h1 align="center">NaviCloud</h1>

<p align="center">
  <b>Kendi müziğin, her cihazda.</b><br/>
  Navidrome / Subsonic için modern, hızlı ve şık bir müzik istemcisi — <b>Android</b> ve <b>Windows</b>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20Windows-7C4DFF?style=flat-square" alt="platform"/>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-8E5BFF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"/>
  <img src="https://img.shields.io/badge/Compose-Multiplatform-6366F1?style=flat-square" alt="compose"/>
  <img src="https://img.shields.io/badge/sürüm-1.0.0-5B34E0?style=flat-square" alt="version"/>
</p>

---

<p align="center">
  <img src="docs/screenshots/home.png" width="215"/>
  &nbsp;
  <img src="docs/screenshots/player.png" width="215"/>
  &nbsp;
  <img src="docs/screenshots/audio.png" width="215"/>
  &nbsp;
  <img src="docs/screenshots/menu.png" width="215"/>
</p>

<p align="center"><sub>Ana sayfa · Şu an çalıyor · Ekolayzer & ses efektleri · Oynatıcı menüsü</sub></p>

---

## Neler var

### 🎧 Çalma
- Kesintisiz (gapless) oynatma, kuyruk yönetimi, sürükle-bırak sıralama
- **Ekolayzer** (5 tür preset) + **ses efektleri**: bas güçlendirme, genişlik, ortam (reverb), ses kazancı
- **Uyku zamanlayıcı**: süreli (10/20/30/60/90 dk) veya "parça/kuyruk bitince dur"
- Senkronize şarkı sözleri, favoriler ve scrobble
- Parça teknik bilgisi (codec / bitrate / örnekleme / kaynak → çıkış)

### 📥 Çevrimdışı & önbellek
- Şarkı indirme + **offline mod** (yalnızca indirilenlerden çalar)
- Akıllı önbellek: metadata (Room), görsel (Coil), akış (LRU) — indirmelerden ayrı depo
- Sıradakini önden yükleme, Wi-Fi-öncelikli veri kullanımı

### 🖥️ Masaüstü (Windows)
- **libmpv** ses motoru (gömülü), tablet düzeni + yan sidebar
- **Windows medya tuşları + kontrol merkezi/flyout** entegrasyonu (SMTC): kapak, başlık, sanatçı, kontroller
- Sistem tepsisi + "kapatınca tepsiye küçült"
- Her zaman üstte **mini oynatıcı** (dalga formu seek bar)

### 🎨 Tasarım
- Spotify/YT Music kalitesinde koyu tema; kapaktan türeyen renk paleti
- Tek yüzeyli morph'lu oynatıcı (mini ↔ tam), ambient spektrum

---

## Mimari

Kotlin Multiplatform + Compose Multiplatform ile tek kod tabanı:

| Modül | İçerik |
|-------|--------|
| `:shared` | Model, ağ (Subsonic/OpenSubsonic), repository, kuyruk çekirdeği, ses sözleşmesi |
| `:sharedUi` | Tüm ekranlar/bileşenler (Compose MP) — Android + masaüstü ortak |
| `:app` | Android: Media3/ExoPlayer, Hilt, Room, `android.media.audiofx` |
| `:desktop` | Windows: libmpv (JNA), sistem tepsisi, mini oynatıcı, SMTC |
| `desktop/smtc-helper` | Rust (windows-rs) — SMTC native yardımcısı |

Ses/EQ davranışı platformdan bağımsız tek sözleşmede tanımlı (preset/band/aralıklar), iki motor da (audiofx ↔ mpv) aynı değerleri uygular.

---

## Sürümler & CI

- Her `vX.Y.Z` tag push'unda GitHub Actions **Android APK** + **Windows kurulumunu** derleyip Release'e yükler.
- Feature branch'lerde push/PR'da hızlı derleme kontrolü (`CI`) çalışır.
- En güncel yapıları [Releases](../../releases) sayfasından indirin.

```bash
# yeni sürüm yayınla
git tag v1.0.1 && git push origin v1.0.1
```

> Not: Android APK şu an debug anahtarıyla imzalanır (adb ile kurulabilir). Mağaza dağıtımı için kendi keystore'unuzu CI secret'ı olarak ekleyin.

---

## Yerel derleme

```bash
# Android (JDK 17)
./gradlew :app:assembleRelease

# Masaüstü dağıtımı (JDK 21 — jpackage)
./gradlew :desktop:createDistributable
# libmpv-2.dll'i desktop/packaging/windows-x64/ altına koyun (shinchiro/zhongfly derlemesi)
```

---

## Lisans

Açık kaynak bileşenlerin tam listesi ve lisansları uygulama içinde **Ayarlar → Hakkında → Açık kaynak lisansları** altında. Öne çıkanlar: Compose Multiplatform, Coil, OkHttp, Media3, libmpv (LGPL), FFmpeg (LGPL), windows-rs.

<p align="center"><sub>❤️ ile, açık kaynakla mümkün oldu.</sub></p>
