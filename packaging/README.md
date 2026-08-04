# NaviCloud — Linux packaging

Linux distribution packages for the desktop build (Compose/JVM + libmpv). They all
build on the `:desktop:createDistributable` output (an app-image with a bundled JRE)
and link against the **system libmpv** for audio (the libmpv+ffmpeg tree is not bundled)
— except the Flatpak, which builds its own libmpv.

## Building

Everything via `scripts/build-release-linux.sh` (WSL/Linux, needs a JDK 21 with jpackage):

```bash
scripts/build-release-linux.sh            # all: rpm + deb + tarball + AppImage
scripts/build-release-linux.sh deb        # .deb only
scripts/build-release-linux.sh rpm        # .rpm only
scripts/build-release-linux.sh tar        # tarball only (AUR/generic)
scripts/build-release-linux.sh appimage   # .AppImage only
scripts/build-release-linux.sh --check    # verify tool paths
```

Outputs go to `dist/` (and the Desktop if present): `navicloud_<ver>_amd64.deb`,
`navicloud-<ver>-1.x86_64.rpm`, `NaviCloud-<ver>-linux-x86_64.tar.gz`,
`NaviCloud-<ver>-x86_64.AppImage`.

| Package | For | Dependency |
|---|---|---|
| **.deb** | Debian/Ubuntu | `libmpv2 \| libmpv1` |
| **.rpm** | Fedora/openSUSE | libmpv (distro package) |
| **AppImage** | Distro-agnostic single file | system libmpv + FUSE |
| **tarball** | Generic; extract to `/opt` or use for AUR | system libmpv |

## Arch (AUR)

`packaging/aur/PKGBUILD` — `navicloud-bin`, installs the release tarball to `/opt/navicloud`.

```bash
cd packaging/aur
makepkg -si            # build + install
makepkg --printsrcinfo > .SRCINFO
```

**On a new version:** bump `pkgver`, pin `sha256sums` to the tarball digest
(`updpkgsums`), regenerate `.SRCINFO`, then push to AUR.

### Verify with Docker (no Arch host)

```bash
# makepkg against the local tarball (before the release is published)
docker run --rm -v "$PWD/dist:/build" archlinux bash -c '
  pacman -Sy --noconfirm --needed base-devel
  useradd -m b && cp /build/PKGBUILD-test /home/b/PKGBUILD
  cp /build/NaviCloud-*-linux-x86_64.tar.gz /home/b/
  chown -R b /home/b
  su b -c "cd ~ && makepkg -f --nodeps --noconfirm && ls *.pkg.tar.zst"
'
```

## Flatpak

`packaging/flatpak/io.github.akinozgen.NaviCloud.yml` — **build-verified**
(WSL/Ubuntu 24.04 + flatpak-builder: builds, installs, launches, loads libmpv).
Since libmpv is not in the Flatpak runtime, it is built from source:
**libplacebo v7.360.1 → libass 0.17.5 → mpv 0.41.0** (`libmpv.so` only). ffmpeg comes
from the `org.freedesktop.Platform.ffmpeg-full` extension. The app + bundled JRE are
installed to `/app` from the release tarball (same app-image as deb/rpm/AppImage).

```bash
flatpak install --user -y flathub org.freedesktop.{Platform,Sdk}//24.08 \
  org.freedesktop.Platform.ffmpeg-full//24.08
flatpak-builder --user --install --force-clean --disable-rofiles-fuse \
  build-dir packaging/flatpak/io.github.akinozgen.NaviCloud.yml
flatpak run io.github.akinozgen.NaviCloud
```

Single-file bundle (the `.flatpak` shipped on the release):

```bash
flatpak-builder --user --force-clean --disable-rofiles-fuse --repo=repo \
  build-dir packaging/flatpak/io.github.akinozgen.NaviCloud.yml
flatpak build-bundle --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo \
  repo NaviCloud-<ver>.flatpak io.github.akinozgen.NaviCloud
```

Manifest notes (gotchas):
- freedesktop-sdk meson uses `lib64` → force libplacebo/mpv with `-Dlibdir=lib`,
  otherwise mpv can't find `libplacebo.pc` (PKG_CONFIG_PATH only looks at `/app/lib`).
- JVM/AWT (Skiko) **needs X11** → `--socket=x11` (not fallback-x11, which hides X11 when Wayland is present).
- MPRIS/tray need `--own-name` grants; they require a real desktop D-Bus session.
- Under WSLg the Skiko OpenGL context fails to init (a WSLg GL limitation — the raw
  app-image fails the same way); it renders on a real GPU/Mesa Linux desktop.
- For a Flathub submission you'd also need `.metainfo.xml` (AppStream) + screenshots +
  app-id ownership verification (github.com/akinozgen). A local build needs none of these.
