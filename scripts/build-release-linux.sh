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
#   scripts/build-release-linux.sh            # her ikisi (rpm + deb)
#   scripts/build-release-linux.sh rpm        # sadece rpm
#   scripts/build-release-linux.sh deb        # sadece deb
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

VERSION="$(grep -oP 'packageVersion = "\K[^"]+' desktop/build.gradle.kts)"
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
Description: Navidrome müzik istemcisi
 NaviCloud — Navidrome/Subsonic sunucuları için masaüstü müzik istemcisi.
 Gömülü Java çalışma ortamıyla gelir; ses motoru olarak sistem libmpv'sini kullanır.
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

case "${1:-all}" in
  --check) check ;;
  rpm)     build_rpm ;;
  deb)     build_deb ;;
  all)     build_appimage; build_rpm; build_deb ;;
  *) echo "Bilinmeyen hedef: $1  (rpm | deb | all | --check)"; exit 2 ;;
esac
echo ">> Bitti."
