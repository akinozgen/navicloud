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

`packaging/flatpak/io.github.akinozgen.NaviCloud.yml` — **BAŞLANGIÇ/WIP**. app + JRE
kurulur; libmpv Flatpak runtime'ında olmadığından bir libmpv modülü (kaynaktan) ya da
BaseApp gerekir. Manifest'in başındaki nota bak. Derleme:

```bash
flatpak-builder --user --install build-dir packaging/flatpak/io.github.akinozgen.NaviCloud.yml
```
