; NaviCloud masaüstü — Inno Setup kurulum betiği (NSIS'in yerine; derleme çok daha hızlı).
; Compose createDistributable çıktısını (app-image) alıp kullanıcı-düzeyi kurulum
; + Başlat Menüsü/Masaüstü kısayolu + kaldırıcı üretir.
; Derleme: ISCC.exe /DAppDir=<app-image yolu> navicloud.iss

#define AppName "NaviCloud"
#define AppVersion "1.5.0"
#define Company "ozgen"
#ifndef AppDir
  #define AppDir "..\build\compose\binaries\main\app\NaviCloud"
#endif

[Setup]
; Sabit AppId: yükseltmeler aynı kaydın üzerine biner (NSIS kurulumundan bağımsız anahtar)
AppId={{7E2B1F04-9C55-4E8B-B9D1-0A6C1E5F0F5B}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Company}
; Kullanıcı-düzeyi kurulum, UAC yok (NSIS RequestExecutionLevel user karşılığı)
PrivilegesRequired=lowest
DefaultDirName={localappdata}\Programs\{#AppName}
DisableProgramGroupPage=yes
OutputDir=.
OutputBaseFilename=NaviCloud-Setup
SetupIconFile=..\icons\navicloud.ico
UninstallDisplayIcon={app}\{#AppName}.exe
; Hız odaklı sıkıştırma: solid+max LZMA (NSIS'te dakikalar sürüyordu) yerine lzma2/fast
Compression=lzma2/fast
SolidCompression=no
; Çalışan uygulamayı kapattır (tepside açık kalmış NaviCloud kurulumu bozmasın)
CloseApplications=yes
WizardStyle=modern

[Languages]
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; app-image içeriğinin tamamı (NaviCloud.exe, app\, runtime\)
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{userprograms}\{#AppName}"; Filename: "{app}\{#AppName}.exe"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\{#AppName}.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppName}.exe"; Description: "NaviCloud'u başlat"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Kurulum dizinini komple temizle (runtime çalışırken üretilen dosyalar dahil)
Type: filesandordirs; Name: "{app}"
