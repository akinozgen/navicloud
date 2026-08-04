# NaviCloud — Linux paketleme

Masaüstü (Compose/JVM + libmpv) için Linux dağıtım paketleri. Hepsi
`:desktop:createDistributable` çıktısını (gömülü JRE'li app-image) temel alır ve
ses için **sistem libmpv**'sine bağlanır (libmpv+ffmpeg ağacı gömülmez).

## Üretim

Tümü `scripts/build-release-linux.sh` ile (WSL/Linux, jpackage'lı JDK 21 gerekir):

```bash
scripts/build-release-linux.sh            # hepsi: rpm + deb + tarball + AppImage
scripts/build-release-linux.sh deb        # sadece .deb
scripts/build-release-linux.sh rpm        # sadece .rpm
scripts/build-release-linux.sh tar        # sadece tarball (AUR/generic)
scripts/build-release-linux.sh appimage   # sadece .AppImage
scripts/build-release-linux.sh --check    # araç yollarını doğrula
```

Çıktılar `dist/` (ve varsa Masaüstü) altına: `navicloud_<sürüm>_amd64.deb`,
`navicloud-<sürüm>-1.x86_64.rpm`, `NaviCloud-<sürüm>-linux-x86_64.tar.gz`,
`NaviCloud-<sürüm>-x86_64.AppImage`.

| Paket | Nasıl | Bağımlılık |
|---|---|---|
| **.deb** | Debian/Ubuntu | `libmpv2 \| libmpv1` |
| **.rpm** | Fedora/openSUSE | libmpv (dağıtım paketi) |
| **AppImage** | Dağıtım-bağımsız tek dosya | sistem libmpv + FUSE |
| **tarball** | Generic; `/opt`'a aç ya da AUR | sistem libmpv |

## Arch (AUR)

`packaging/aur/PKGBUILD` — release tarball'ını `/opt/navicloud`'a kuran `navicloud-bin`.

```bash
cd packaging/aur
makepkg -si            # derle + kur
makepkg --printsrcinfo > .SRCINFO
```

**Yeni sürümde:** `pkgver`i güncelle, `sha256sums`i tarball özetiyle sabitle
(`updpkgsums`), `.SRCINFO`yu yenile, AUR'a push et.

### Docker ile doğrulama (Arch yoksa)

```bash
# yerel tarball'a karşı makepkg (release yayınlanmadan)
docker run --rm -v "$PWD/dist:/build" archlinux bash -c '
  pacman -Sy --noconfirm --needed base-devel
  useradd -m b && cp /build/PKGBUILD-test /home/b/PKGBUILD
  cp /build/NaviCloud-*-linux-x86_64.tar.gz /home/b/
  chown -R b /home/b
  su b -c "cd ~ && makepkg -f --nodeps --noconfirm && ls *.pkg.tar.zst"
'
```

## Flatpak

`packaging/flatpak/io.github.akinozgen.NaviCloud.yml` — **derleme doğrulandı**
(WSL/Ubuntu 24.04 + flatpak-builder: derlenir, kurulur, açılır, libmpv yüklenir).
libmpv Flatpak runtime'ında olmadığından kaynaktan derlenir:
**libplacebo v7.360.1 → libass 0.17.5 → mpv 0.41.0** (yalnız `libmpv.so`). ffmpeg
`org.freedesktop.Platform.ffmpeg-full` extension'ından gelir. Uygulama + gömülü JRE
release tarball'ından `/app`'e kurulur (deb/rpm/AppImage ile aynı app-image).

```bash
flatpak install --user -y flathub org.freedesktop.{Platform,Sdk}//24.08 \
  org.freedesktop.Platform.ffmpeg-full//24.08
flatpak-builder --user --install --force-clean --disable-rofiles-fuse \
  build-dir packaging/flatpak/io.github.akinozgen.NaviCloud.yml
flatpak run io.github.akinozgen.NaviCloud
```

Manifest notları (tuzaklar):
- freedesktop-sdk meson `lib64` kullanır → libplacebo/mpv `-Dlibdir=lib` ile zorlanır,
  yoksa mpv `libplacebo.pc`'yi bulamaz (PKG_CONFIG_PATH yalnız `/app/lib` bakar).
- JVM/AWT (Skiko) **X11 ister** → `--socket=x11` (fallback-x11, wayland varken X11'i vermez).
- MPRIS/tepsi için `--own-name` grant'leri; gerçek bir masaüstü D-Bus session'ı gerektirir.
- WSLg altında Skiko OpenGL context açılamaz (WSLg GL kısıtı — ham app-image de aynı);
  gerçek GPU/Mesa'lı Linux masaüstünde render eder.
- Flathub'a submit için ek: `.metainfo.xml` (AppStream) + ekran görüntüsü + app-id sahiplik
  doğrulaması (github.com/akinozgen). Yerel derleme bunları istemez.
