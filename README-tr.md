<p align="center">
  <img src="docs/icon.png" width="128" alt="NaviCloud" />
</p>

<h1 align="center">NaviCloud</h1>

<p align="center">
  <b>Kendi müziğin, her cihazda.</b><br/>
  Navidrome / Subsonic için modern, hızlı ve şık bir müzik istemcisi — <b>Android</b>, <b>Windows</b> ve <b>Linux</b>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux-7C4DFF?style=flat-square" alt="platform"/>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-8E5BFF?style=flat-square&logo=kotlin&logoColor=white" alt="kotlin"/>
  <img src="https://img.shields.io/badge/Compose-Multiplatform-6366F1?style=flat-square" alt="compose"/>
  <img src="https://img.shields.io/github/v/release/akinozgen/navicloud?style=flat-square&color=5B34E0&label=sürüm" alt="version"/>
</p>

<p align="center">
  <a href="README.md">English</a> · <b>Türkçe</b>
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

## Kurulum

Dosyaları [**son sürümden**](../../releases/latest) indirin, sonra platformunuza bakın. Linux **`.deb` / `.rpm` / AppImage / tarball / AUR** için **libmpv** (ses motoru) gerekir — önce dağıtımınızın `mpv` / `libmpv` paketini kurun. **Flatpak** kendi libmpv'sini taşıdığı için orada bu adımı atlayın.

<details open>
<summary><b>Windows</b></summary>

**`NaviCloud-*-Setup.exe`**'yi indirip çalıştırın. Kullanıcı-düzeyi kurulum — yönetici gerekmez.
</details>

<details>
<summary><b>Android</b></summary>

**`NaviCloud-*.apk`**'yi indirin, açın, sorarsa "bilinmeyen uygulamalara izin ver" deyin.
</details>

<details>
<summary><b>Linux — Debian / Ubuntu (.deb)</b></summary>

```bash
sudo apt install ./NaviCloud-*.amd64.deb
```
</details>

<details>
<summary><b>Linux — Fedora / openSUSE (.rpm)</b></summary>

```bash
sudo dnf install ./NaviCloud-*.x86_64.rpm      # openSUSE: sudo zypper install ./NaviCloud-*.x86_64.rpm
```
</details>

<details>
<summary><b>Linux — AppImage (her dağıtım)</b></summary>

```bash
sudo pacman -S mpv        # veya: sudo apt install libmpv2  /  sudo dnf install mpv-libs
chmod +x NaviCloud-*-x86_64.AppImage
./NaviCloud-*-x86_64.AppImage
```
</details>

<details>
<summary><b>Linux — Arch (AUR)</b></summary>

```bash
yay -S navicloud-bin      # veya: paru -S navicloud-bin
```
</details>

<details>
<summary><b>Linux — Flatpak</b></summary>

```bash
flatpak remote-add --if-not-exists --user flathub https://flathub.org/repo/flathub.flatpakrepo
flatpak install --user ./NaviCloud-*.flatpak      # libmpv + runtime otomatik gelir
flatpak run io.github.akinozgen.NaviCloud
```
</details>

<details>
<summary><b>Linux — taşınabilir tarball</b></summary>

```bash
sudo tar -xzf NaviCloud-*-linux-x86_64.tar.gz -C /opt
/opt/navicloud/bin/NaviCloud
```
</details>

---

## Neler var

### 🎧 Çalma
- Kesintisiz (gapless) oynatma, kuyruk yönetimi, sürükle-bırak sıralama
- **Ekolayzer** (5 tür preset) + **ses efektleri**: bas güçlendirme, genişlik, ortam (reverb), ses kazancı
- **Uyku zamanlayıcı**: süreli (10/20/30/60/90 dk) veya "parça/kuyruk bitince dur"
- Senkronize şarkı sözleri, favoriler ve scrobble
- Parça teknik bilgisi (codec / bitrate / örnekleme / kaynak → çıkış)

### 📡 Uzaktan kumanda (LAN)
- **Spotify Connect tarzı** kumanda: bir cihaz seç ve kontrol et, ya da çalmayı cihazlar arasında devret
- Tamamen **simetrik** — her istemci hem kontrol eder hem kontrol edilir
- Yerel ağda **mDNS** ile cihaz keşfi, **PIN** veya ortak parola ile eşleştirme, "cihazı unut"
- Self-hosted ve **yalnızca LAN**: hiçbir şey ağından çıkmaz

### 📥 Çevrimdışı & önbellek
- Şarkı indirme + **offline mod** (yalnızca indirilenlerden çalar)
- Akıllı önbellek: metadata (Room), görsel (Coil), akış (LRU) — indirmelerden ayrı depo
- Sıradakini önden yükleme, Wi-Fi-öncelikli veri kullanımı

### 🖥️ Masaüstü (Windows & Linux)
- **libmpv** ses motoru (Windows'ta gömülü, Linux'ta sistem libmpv'si), tablet düzeni + yan sidebar
- **Windows medya tuşları + kontrol merkezi/flyout** entegrasyonu (SMTC): kapak, başlık, sanatçı, kontroller
- Sistem tepsisi + "kapatınca tepsiye küçült"
- Her zaman üstte **mini oynatıcı** (dalga formu seek bar)

### 🎨 Tasarım
- Spotify/YT Music kalitesinde koyu tema; kapaktan türeyen renk paleti
- Tek yüzeyli morph'lu oynatıcı (mini ↔ tam), ambient spektrum

### 🌐 Diller
- **İngilizce** (varsayılan) ve **Türkçe** — Ayarlar'dan değiştirilebilir

---

## Mimari

Kotlin Multiplatform + Compose Multiplatform ile tek kod tabanı:

| Modül | İçerik |
|-------|--------|
| `:shared` | Model, ağ (Subsonic/OpenSubsonic), repository, kuyruk çekirdeği, ses sözleşmesi, i18n kataloğu |
| `:sharedUi` | Tüm ekranlar/bileşenler (Compose MP) — Android + masaüstü ortak |
| `:app` | Android: Media3/ExoPlayer, Hilt, Room, `android.media.audiofx` |
| `:desktop` | Windows: libmpv (JNA), sistem tepsisi, mini oynatıcı, SMTC |
| `desktop/smtc-helper` | Rust (windows-rs) — SMTC native yardımcısı |

Ses/EQ davranışı platformdan bağımsız tek sözleşmede tanımlı (preset/band/aralıklar), iki motor da (audiofx ↔ mpv) aynı değerleri uygular.

---

## Sürümler & CI

- Her `vX.Y.Z` tag push'unda GitHub Actions **Android APK** + **Windows kurulumu** + **Linux paketlerini** (`.deb`, `.rpm`, `.AppImage`, `.tar.gz`) derleyip Release'e yükler.
- Feature branch'lerde push/PR'da hızlı derleme kontrolü (`CI`) çalışır.
- En güncel yapıları [Releases](../../releases/latest) sayfasından indirin. Linux paketleme ayrıntıları (AUR, Flatpak) [`packaging/`](packaging/) altında.

```bash
# yeni sürüm yayınla
git tag v1.3.1 && git push origin v1.3.1
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

[![Lisans: GPL v3](https://img.shields.io/badge/Lisans-GPLv3-blue.svg)](LICENSE)

NaviCloud **GNU General Public License v3.0** ile lisanslıdır — bkz. [LICENSE](LICENSE). Masaüstü derlemesi libmpv + FFmpeg'in GPL derlemelerini paketlediği için tüm proje GPLv3'tür.

Açık kaynak bileşenlerin tam listesi uygulama içinde **Ayarlar → Hakkında → Açık kaynak lisansları** altında. Öne çıkanlar: Compose Multiplatform, Coil, OkHttp, Media3 (Apache-2.0); libmpv, FFmpeg (GPL-2.0+); windows-rs (MIT/Apache-2.0).

<p align="center"><sub>❤️ ile, açık kaynakla mümkün oldu.</sub></p>
