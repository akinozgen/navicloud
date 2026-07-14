#!/usr/bin/env bash
# NaviCloud sürüm derleyici — tek komutla APK ve/veya masaüstü installer üretir,
# çıktıları Masaüstü'ne bırakır. Git-bash (Windows) için yazıldı.
#
# Kullanım:
#   scripts/build-release.sh              # her ikisi (apk + desktop)
#   scripts/build-release.sh desktop      # sadece masaüstü installer (Setup.exe)
#   scripts/build-release.sh apk          # sadece Android release APK
#   scripts/build-release.sh --check      # sadece araç yollarını doğrula, çık
#
# Araç yolları (override edilebilir env değişkenleri):
#   JBR            Android/gradle derlemesi için JDK (vars: Android Studio jbr)
#   NAVICLOUD_JDK  jpackage'lı JDK — masaüstü dağıtımı için (vars: ~/.navicloud-build)
#   NAVICLOUD_ISCC Inno Setup ISCC.exe yolu (vars: %LOCALAPPDATA%/Programs/Inno Setup 6)
set -euo pipefail

ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$ROOT"
DESKTOP_OUT="$(cygpath -u "$(cmd.exe /c 'echo %USERPROFILE%' 2>/dev/null | tr -d '\r')")/Desktop"
[ -d "$DESKTOP_OUT" ] || DESKTOP_OUT="$HOME/Desktop"

# --- Araç çözümleme (env → kalıcı ~/.navicloud-build → temp scratchpad → PATH) ---
first_glob() { for p in $1; do [ -e "$p" ] && { echo "$p"; return; }; done; }

JBR="${JBR:-C:/Program Files/Android/Android Studio/jbr}"

JDK="${NAVICLOUD_JDK:-}"
if [ -z "$JDK" ]; then
  JDK="$(first_glob "$HOME/.navicloud-build/jdk21/*/")"
  [ -z "$JDK" ] && JDK="$(first_glob "/c/Users/$USER/AppData/Local/Temp/claude/*/*/scratchpad/jdk21/*/")"
  JDK="${JDK%/}"
fi

ISCC="${NAVICLOUD_ISCC:-}"
if [ -z "$ISCC" ]; then
  ISCC="$(first_glob "$LOCALAPPDATA/Programs/Inno Setup 6/ISCC.exe" 2>/dev/null || true)"
  [ -z "$ISCC" ] && ISCC="$(first_glob "/c/Program Files (x86)/Inno Setup 6/ISCC.exe")"
  [ -z "$ISCC" ] && ISCC="$(first_glob "/c/Program Files/Inno Setup 6/ISCC.exe")"
  [ -z "$ISCC" ] && command -v ISCC >/dev/null 2>&1 && ISCC="$(command -v ISCC)"
fi

gradlew() { local jh="$1"; shift; JAVA_HOME="$jh" "$jh/bin/java.exe" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@" --console=plain; }

check() {
  echo "ROOT        : $ROOT"
  echo "Desktop out : $DESKTOP_OUT"
  echo "JBR         : $JBR $( [ -x "$JBR/bin/java.exe" ] && echo OK || echo 'YOK!')"
  echo "jpackage JDK: ${JDK:-<bulunamadı>} $( [ -x "${JDK:-}/bin/jpackage.exe" ] && echo OK || echo 'YOK!')"
  echo "Inno ISCC   : ${ISCC:-<bulunamadı>} $( [ -x "${ISCC:-x}" ] && echo OK || echo 'YOK!')"
}

build_apk() {
  echo ">> Android release APK derleniyor…"
  [ -x "$JBR/bin/java.exe" ] || { echo "HATA: JBR yok ($JBR)"; exit 1; }
  gradlew "$JBR" :app:assembleRelease
  cp "app/build/outputs/apk/release/app-release.apk" "$DESKTOP_OUT/NaviCloud-release.apk"
  echo ">> $DESKTOP_OUT/NaviCloud-release.apk"
}

build_desktop() {
  echo ">> Masaüstü dağıtımı (jpackage) + Inno Setup installer üretiliyor…"
  [ -x "${JDK:-}/bin/jpackage.exe" ] || { echo "HATA: jpackage'lı JDK yok. NAVICLOUD_JDK ile yol ver."; exit 1; }
  [ -x "${ISCC:-x}" ] || { echo "HATA: ISCC yok. NAVICLOUD_ISCC ile yol ver."; exit 1; }
  gradlew "$JDK" :desktop:createDistributable
  local appdir; appdir="$(cygpath -w "$ROOT/desktop/build/compose/binaries/main/app/NaviCloud")"
  ( cd desktop/installer && "$ISCC" "/DAppDir=$appdir" navicloud.iss )
  cp "desktop/installer/NaviCloud-Setup.exe" "$DESKTOP_OUT/NaviCloud-Setup.exe"
  echo ">> $DESKTOP_OUT/NaviCloud-Setup.exe"
}

case "${1:-all}" in
  --check) check ;;
  apk)     build_apk ;;
  desktop) build_desktop ;;
  all)     build_apk; build_desktop ;;
  *) echo "Bilinmeyen hedef: $1  (apk | desktop | all | --check)"; exit 2 ;;
esac
echo ">> Bitti."
