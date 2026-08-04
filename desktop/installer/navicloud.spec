# NaviCloud rpm spec — jpackage'ın packageRpm'i yerine kullanılır.
#
# Neden: jpackage rpm'i menü girdisini %post scriptinde xdg-desktop-menu ile
# kuruyor ve bu, dnf'nin SELinux-kısıtlı scriptlet bağlamında sessizce
# başarısız olabiliyor (Fedora'da uygulama menüde çıkmıyordu). Burada .desktop
# ve ikon scriptsiz, doğrudan paket içeriği olarak kurulur. Ayrıca jpackage'ın
# yazamadığı libmpv bağımlılığı (Requires) doğru bildirilir.
#
# Derleme: scripts/build-release-linux.sh (rpmbuild'i --define'larla çağırır:
#   ver=<sürüm> appimage=<createDistributable çıktısı> srcdir=<installer dizini>
#   icon=<png>)

Name:           navicloud
Version:        %{ver}
Release:        1
Summary:        Navidrome/Subsonic music client
License:        GPL-3.0-or-later
URL:            https://github.com/akinozgen/navicloud
Requires:       mpv-libs
AutoReqProv:    no
%define _build_id_links none
%define __strip /bin/true

%description
NaviCloud — a desktop music client for Navidrome/Subsonic servers.
Ships with a bundled Java runtime; uses the system libmpv as its audio engine.

%install
mkdir -p %{buildroot}/opt/navicloud
cp -a %{appimage}/. %{buildroot}/opt/navicloud/
install -Dm644 %{srcdir}/navicloud.desktop %{buildroot}%{_datadir}/applications/navicloud.desktop
install -Dm644 %{icon} %{buildroot}%{_datadir}/icons/hicolor/256x256/apps/navicloud.png

%files
/opt/navicloud
%{_datadir}/applications/navicloud.desktop
%{_datadir}/icons/hicolor/256x256/apps/navicloud.png
