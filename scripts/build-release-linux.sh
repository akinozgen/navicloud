#!/usr/bin/env bash
# NaviCloud Linux sürüm derleyici — rpm + deb üretir.
# (Windows karşılığı: scripts/build-release.sh / build-release.ps1)
#
# jpackage'ın packageRpm/packageDeb'i KULLANILMAZ: menü girdisini %post
# scriptiyle kuruyordu ve dnf'nin SELinux-kısıtlı scriptlet bağlamında sessizce
# başarısız oluyordu. Burada :desktop:createDistributable çıktısı (app-image)
# kendi spec/control dosyalarımızla paketlenir: .desktop + ikon paket içeriği
# olarak /usr/share altına girer, libmpv bağımlılığı da doğru bildirilir.
#
# Kullanım:
#   scripts/build-release-linux.sh            # hepsi (rpm + deb + tarball + AppImage)
#   scripts/build-release-linux.sh rpm        # sadece rpm
#   scripts/build-release-linux.sh deb        # sadece deb
#   scripts/build-release-linux.sh tar        # sadece tarball (AUR/generic)
#   scripts/build-release-linux.sh appimage   # sadece AppImage
#   scripts/build-release-linux.sh --check    # araç yollarını doğrula, çık
#
# Çıktılar: dist/ altına; ayrıca Masaüstü varsa oraya da kopyalanır.
#
# Araçlar:
#   NAVICLOUD_JDK  jpackage'lı JDK 21 (vars: ~/.navicloud-build/jdk21/*)
#   rpm: rpmbuild (dnf install rpm-build)
#   deb: dpkg-deb + fakeroot (dnf install dpkg fakeroot)
set -euo pipefail

ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$ROOT"

DEFAULT_VERSION="$(sed -n 's/^navicloudVersion=//p' gradle.properties | head -n1 | tr -d '\r')"
VERSION="${NAVICLOUD_VERSION:-$DEFAULT_VERSION}"
VERSION="${VERSION#v}"
[ -n "$VERSION" ] || { echo "HATA: NaviCloud sürümü bulunamadı."; exit 1; }
export NAVICLOUD_VERSION="$VERSION"
APPIMAGE="$ROOT/desktop/build/compose/binaries/main/app/NaviCloud"
INSTALLER="$ROOT/desktop/installer"
ICON="$ROOT/desktop/icons/navicloud.png"
DIST="$ROOT/dist"

# Masaüstü dizini (yerelleştirilmiş — Türkçe sistemde ~/Masaüstü); yoksa sadece dist/
DESKTOP_OUT="$(command -v xdg-user-dir >/dev/null 2>&1 && xdg-user-dir DESKTOP || echo "$HOME/Desktop")"

first_glob() { for p in $1; do [ -e "$p" ] && { echo "$p"; return; }; done; }

JDK="${NAVICLOUD_JDK:-}"
if [ -z "$JDK" ]; then
  JDK="$(first_glob "$HOME/.navicloud-build/jdk21/*/")"
  JDK="${JDK%/}"
fi

gradlew() { JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" ./gradlew "$@" --console=plain; }

check() {
  echo "ROOT        : $ROOT"
  echo "Sürüm       : $VERSION"
  echo "Desktop out : $DESKTOP_OUT"
  echo "jpackage JDK: ${JDK:-<bulunamadı>} $( [ -x "${JDK:-}/bin/jpackage" ] && echo OK || echo 'YOK!')"
  echo "rpmbuild    : $(command -v rpmbuild || echo 'YOK! (dnf install rpm-build)')"
  echo "dpkg-deb    : $(command -v dpkg-deb || echo 'YOK! (dnf install dpkg)')"
  echo "fakeroot    : $(command -v fakeroot || echo 'YOK! (dnf install fakeroot)')"
  echo "curl        : $(command -v curl || echo 'YOK! (appimage için gerekli)')"
  echo "appimagetool: $( [ -x "$HOME/.navicloud-build/appimagetool-x86_64.AppImage" ] && echo 'cache OK' || echo 'ilk çalıştırmada indirilir')"
  echo "libmpv      : $(ldconfig -p 2>/dev/null | awk '/libmpv\.so/{print $NF; exit}' || true)"
}

require_jdk() { [ -x "${JDK:-}/bin/jpackage" ] || { echo "HATA: jpackage'lı JDK yok. NAVICLOUD_JDK ile yol ver."; exit 1; }; }

build_appimage() {
  require_jdk
  echo ">> App-image derleniyor (createDistributable)…"
  gradlew :desktop:createDistributable
  [ -x "$APPIMAGE/bin/NaviCloud" ] || { echo "HATA: app-image üretilemedi: $APPIMAGE"; exit 1; }
}

publish() { # $1 = paket dosyası
  mkdir -p "$DIST"
  cp "$1" "$DIST/"
  if [ -d "$DESKTOP_OUT" ]; then # CI'da Masaüstü yok — sadece dist/
    cp "$1" "$DESKTOP_OUT/"
    echo ">> $DESKTOP_OUT/$(basename "$1")"
  fi
  echo ">> $DIST/$(basename "$1")"
}

build_rpm() {
  command -v rpmbuild >/dev/null || { echo "HATA: rpmbuild yok (dnf install rpm-build)"; exit 1; }
  [ -x "$APPIMAGE/bin/NaviCloud" ] || build_appimage
  echo ">> RPM üretiliyor…"
  local top; top="$(mktemp -d)"
  rpmbuild -bb "$INSTALLER/navicloud.spec" \
    --define "_topdir $top" \
    --define "ver $VERSION" \
    --define "appimage $APPIMAGE" \
    --define "srcdir $INSTALLER" \
    --define "icon $ICON" \
    --quiet
  publish "$top/RPMS/$(uname -m)/navicloud-$VERSION-1.$(uname -m).rpm"
  rm -rf "$top"
}

build_deb() {
  command -v dpkg-deb >/dev/null || { echo "HATA: dpkg-deb yok (dnf install dpkg)"; exit 1; }
  [ -x "$APPIMAGE/bin/NaviCloud" ] || build_appimage
  echo ">> DEB üretiliyor…"
  local stage; stage="$(mktemp -d)"
  mkdir -p "$stage/opt/navicloud" "$stage/usr/share/applications" \
           "$stage/usr/share/icons/hicolor/256x256/apps" "$stage/DEBIAN"
  cp -a "$APPIMAGE/." "$stage/opt/navicloud/"
  install -m644 "$INSTALLER/navicloud.desktop" "$stage/usr/share/applications/navicloud.desktop"
  install -m644 "$ICON" "$stage/usr/share/icons/hicolor/256x256/apps/navicloud.png"
  cat > "$stage/DEBIAN/control" <<EOF
Package: navicloud
Version: $VERSION
Architecture: amd64
Maintainer: Akın Özgen <akin@quartbilisim.net>
Depends: libmpv2 | libmpv1
Section: sound
Priority: optional
Description: Navidrome/Subsonic music client
 NaviCloud — a desktop music client for Navidrome/Subsonic servers.
 Ships with a bundled Java runtime; uses the system libmpv as its audio engine.
EOF
  local out="$stage/../navicloud_${VERSION}_amd64.deb"
  if command -v fakeroot >/dev/null; then
    fakeroot dpkg-deb --build "$stage" "$out" >/dev/null
  else
    dpkg-deb --root-owner-group --build "$stage" "$out" >/dev/null
  fi
  publish "$out"
  rm -rf "$stage" "$out"
}

# --- appimagetool: indir (yoksa) + FUSE'suz çalıştır (WSL/CI'da FUSE yok) ---
APPIMAGETOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
get_appimagetool() {
  local t="$HOME/.navicloud-build/appimagetool-x86_64.AppImage"
  if [ ! -x "$t" ]; then
    echo ">> appimagetool indiriliyor…" >&2
    mkdir -p "$(dirname "$t")"
    curl -fsSL "$APPIMAGETOOL_URL" -o "$t" && chmod +x "$t"
  fi
  echo "$t"
}

# Generic tar.gz (app-image + .desktop + ikon) — AUR PKGBUILD bunu tüketir, ayrıca
# elle /opt'a açılabilir. Portatif değil: sistem libmpv'sini kullanır (deb/rpm gibi).
build_tarball() {
  [ -x "$APPIMAGE/bin/NaviCloud" ] || build_appimage
  echo ">> Tarball (app-image) üretiliyor…"
  local stage; stage="$(mktemp -d)"
  cp -a "$APPIMAGE" "$stage/navicloud"
  install -Dm644 "$INSTALLER/navicloud.desktop" "$stage/navicloud/navicloud.desktop"
  install -Dm644 "$ICON" "$stage/navicloud/navicloud.png"
  local out="$stage/../NaviCloud-${VERSION}-linux-x86_64.tar.gz"
  tar -C "$stage" -czf "$out" navicloud
  publish "$out"
  rm -rf "$stage" "$out"
}

# AppImage (tek dosya, dağıtım-bağımsız). Gömülü JRE + app içerir; ses için sistem
# libmpv'sine bağlıdır (bilinçli — libmpv+ffmpeg bağımlılık ağacını gömmeyiz).
build_appimage_pkg() {
  [ -x "$APPIMAGE/bin/NaviCloud" ] || build_appimage
  echo ">> AppImage üretiliyor…"
  local base; base="$(mktemp -d)"; local ad="$base/NaviCloud.AppDir"
  mkdir -p "$ad"
  cp -a "$APPIMAGE/." "$ad/"
  cat > "$ad/AppRun" <<'EOS'
#!/bin/bash
HERE="$(dirname "$(readlink -f "${0}")")"
exec "$HERE/bin/NaviCloud" "$@"
EOS
  chmod +x "$ad/AppRun"
  cat > "$ad/navicloud.desktop" <<EOS
[Desktop Entry]
Name=NaviCloud
Comment=Navidrome music client
Comment[tr]=Navidrome müzik istemcisi
Exec=NaviCloud
Icon=navicloud
Terminal=false
Type=Application
Categories=AudioVideo;Audio;Player;
EOS
  install -m644 "$ICON" "$ad/navicloud.png"
  cp "$ICON" "$ad/.DirIcon"
  local tool; tool="$(get_appimagetool)"
  local out="$base/NaviCloud-${VERSION}-x86_64.AppImage"
  ( cd "$base" && ARCH=x86_64 "$tool" --appimage-extract-and-run NaviCloud.AppDir "$out" )
  publish "$out"
  rm -rf "$base"
}

case "${1:-all}" in
  --check)  check ;;
  rpm)      build_rpm ;;
  deb)      build_deb ;;
  tar)      build_tarball ;;
  appimage) build_appimage_pkg ;;
  all)      build_appimage; build_rpm; build_deb; build_tarball; build_appimage_pkg ;;
  *) echo "Bilinmeyen hedef: $1  (rpm | deb | tar | appimage | all | --check)"; exit 2 ;;
esac
echo ">> Bitti."
