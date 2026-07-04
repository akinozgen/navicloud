<#
.SYNOPSIS
  NaviCloud sürüm derleyici (Windows/PowerShell). APK ve/veya masaüstü
  installer üretir, çıktıları Masaüstü'ne bırakır.

.DESCRIPTION
  Araç yollarını (JBR / jpackage'lı JDK / makensis) otomatik çözer:
  env override -> kalıcı ~/.navicloud-build -> temp scratchpad -> PATH.

.PARAMETER Target
  all (varsayılan) | apk | desktop | check

.EXAMPLE
  ./scripts/build-release.ps1 desktop
  ./scripts/build-release.ps1 check
#>
param([ValidateSet('all','apk','desktop','check')][string]$Target = 'all')

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot   # scripts/ -> repo kökü
Set-Location $Root
$DesktopOut = Join-Path $env:USERPROFILE 'Desktop'

function First-File([string[]]$patterns) {
    foreach ($p in $patterns) {
        $f = Get-ChildItem -Path $p -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($f) { return $f.FullName }
    }
    return $null
}

# --- Araç çözümleme ---
$JBR = if ($env:JBR) { $env:JBR } else { 'C:\Program Files\Android\Android Studio\jbr' }

$JDK = $env:NAVICLOUD_JDK
if (-not $JDK) {
    $jp = First-File @(
        "$HOME\.navicloud-build\jdk21\*\bin\jpackage.exe",
        "$env:LOCALAPPDATA\Temp\claude\*\*\scratchpad\jdk21\*\bin\jpackage.exe"
    )
    if ($jp) { $JDK = Split-Path -Parent (Split-Path -Parent $jp) }  # ...\bin\jpackage.exe -> jdk kökü
}

$NSIS = $env:NAVICLOUD_NSIS
if (-not $NSIS) {
    $NSIS = First-File @(
        "$HOME\.navicloud-build\nsis-*\Bin\makensis.exe",
        "$env:LOCALAPPDATA\Temp\claude\*\*\scratchpad\nsis-*\Bin\makensis.exe"
    )
    if (-not $NSIS) { $c = Get-Command makensis -ErrorAction SilentlyContinue; if ($c) { $NSIS = $c.Source } }
}

function Invoke-Gradle([string]$JavaHome, [string[]]$GradleArgs) {
    $env:JAVA_HOME = $JavaHome
    & "$JavaHome\bin\java.exe" -cp 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain @GradleArgs --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle basarisiz: $($GradleArgs -join ' ')" }
}

function Show-Check {
    Write-Host "ROOT        : $Root"
    Write-Host "Desktop out : $DesktopOut"
    $okJbr = Test-Path "$JBR\bin\java.exe"
    $okJdk = $JDK -and (Test-Path "$JDK\bin\jpackage.exe")
    $okNsis = $NSIS -and (Test-Path $NSIS)
    Write-Host "JBR         : $JBR $(if($okJbr){'OK'}else{'YOK!'})"
    Write-Host "jpackage JDK: $(if($JDK){$JDK}else{'<bulunamadi>'}) $(if($okJdk){'OK'}else{'YOK!'})"
    Write-Host "makensis    : $(if($NSIS){$NSIS}else{'<bulunamadi>'}) $(if($okNsis){'OK'}else{'YOK!'})"
}

function Build-Apk {
    Write-Host '>> Android release APK derleniyor...'
    if (-not (Test-Path "$JBR\bin\java.exe")) { throw "JBR yok: $JBR" }
    Invoke-Gradle $JBR @(':app:assembleRelease')
    Copy-Item 'app\build\outputs\apk\release\app-release.apk' "$DesktopOut\NaviCloud-release.apk" -Force
    Write-Host ">> $DesktopOut\NaviCloud-release.apk"
}

function Build-Desktop {
    Write-Host '>> Masaustu dagitimi (jpackage) + NSIS installer uretiliyor...'
    if (-not ($JDK -and (Test-Path "$JDK\bin\jpackage.exe"))) { throw "jpackage'li JDK yok. NAVICLOUD_JDK ile yol ver." }
    if (-not ($NSIS -and (Test-Path $NSIS))) { throw "makensis yok. NAVICLOUD_NSIS ile yol ver." }
    Invoke-Gradle $JDK @(':desktop:createDistributable')
    $AppDir = Join-Path $Root 'desktop\build\compose\binaries\main\app\NaviCloud'
    Push-Location 'desktop\installer'
    try {
        & $NSIS "-DAPPDIR=$AppDir" 'navicloud.nsi'
        if ($LASTEXITCODE -ne 0) { throw "makensis basarisiz ($LASTEXITCODE)" }
    } finally { Pop-Location }
    Copy-Item 'desktop\installer\NaviCloud-Setup.exe' "$DesktopOut\NaviCloud-Setup.exe" -Force
    Write-Host ">> $DesktopOut\NaviCloud-Setup.exe"
}

switch ($Target) {
    'check'   { Show-Check }
    'apk'     { Build-Apk }
    'desktop' { Build-Desktop }
    'all'     { Build-Apk; Build-Desktop }
}
Write-Host '>> Bitti.'
