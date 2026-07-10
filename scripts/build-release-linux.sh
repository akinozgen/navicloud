#!/usr/bin/env bash
# NaviCloud Linux sürüm derleyici — rpm + deb üretir, çıktıları Masaüstü'ne bırakır.
# (Windows karşılığı: scripts/build-release.sh / build-release.ps1)
#
# Kullanım:
#   scripts/build-release-linux.sh            # her ikisi (rpm + deb)
#   scripts/build-release-linux.sh rpm        # sadece rpm
#   scripts/build-release-linux.sh deb        # sadece deb
#   scripts/build-release-linux.sh --check    # araç yollarını doğrula, çık
#
# Araçlar:
#   NAVICLOUD_JDK  jpackage'lı JDK 21 (vars: ~/.navicloud-build/jdk21/*)
#   rpm: rpmbuild (dnf install rpm-build)
#   deb: dpkg-deb + fakeroot (dnf install dpkg fakeroot)
set -euo pipefail

ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$ROOT"

# Masaüstü dizini (yerelleştirilmiş — Türkçe sistemde ~/Masaüstü)
DESKTOP_OUT="$(command -v xdg-user-dir >/dev/null 2>&1 && xdg-user-dir DESKTOP || echo "$HOME/Desktop")"
[ -d "$DESKTOP_OUT" ] || DESKTOP_OUT="$HOME"

first_glob() { for p in $1; do [ -e "$p" ] && { echo "$p"; return; }; done; }

JDK="${NAVICLOUD_JDK:-}"
if [ -z "$JDK" ]; then
  JDK="$(first_glob "$HOME/.navicloud-build/jdk21/*/")"
  JDK="${JDK%/}"
fi

gradlew() { JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" ./gradlew "$@" --console=plain; }

OUT_DIR="desktop/build/compose/binaries/main"

check() {
  echo "ROOT        : $ROOT"
  echo "Desktop out : $DESKTOP_OUT"
  echo "jpackage JDK: ${JDK:-<bulunamadı>} $( [ -x "${JDK:-}/bin/jpackage" ] && echo OK || echo 'YOK!')"
  echo "rpmbuild    : $(command -v rpmbuild || echo 'YOK! (dnf install rpm-build)')"
  echo "dpkg-deb    : $(command -v dpkg-deb || echo 'YOK! (dnf install dpkg)')"
  echo "fakeroot    : $(command -v fakeroot || echo 'YOK! (dnf install fakeroot)')"
  echo "libmpv      : $(ldconfig -p 2>/dev/null | awk '/libmpv\.so/{print $NF; exit}' || true)"
}

require_jdk() { [ -x "${JDK:-}/bin/jpackage" ] || { echo "HATA: jpackage'lı JDK yok. NAVICLOUD_JDK ile yol ver."; exit 1; }; }

build_rpm() {
  require_jdk
  command -v rpmbuild >/dev/null || { echo "HATA: rpmbuild yok (dnf install rpm-build)"; exit 1; }
  echo ">> RPM üretiliyor…"
  gradlew :desktop:packageRpm
  local f; f="$(ls "$OUT_DIR"/rpm/navicloud-*.rpm | head -n1)"
  cp "$f" "$DESKTOP_OUT/"
  echo ">> $DESKTOP_OUT/$(basename "$f")"
}

build_deb() {
  require_jdk
  command -v dpkg-deb >/dev/null || { echo "HATA: dpkg-deb yok (dnf install dpkg)"; exit 1; }
  echo ">> DEB üretiliyor…"
  gradlew :desktop:packageDeb
  local f; f="$(ls "$OUT_DIR"/deb/navicloud*.deb | head -n1)"
  cp "$f" "$DESKTOP_OUT/"
  echo ">> $DESKTOP_OUT/$(basename "$f")"
}

case "${1:-all}" in
  --check) check ;;
  rpm)     build_rpm ;;
  deb)     build_deb ;;
  all)     build_rpm; build_deb ;;
  *) echo "Bilinmeyen hedef: $1  (rpm | deb | all | --check)"; exit 2 ;;
esac
echo ">> Bitti."
