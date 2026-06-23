# TV-Bridge — Install Android Studio, build APKs, push to devices (Windows)
# Usage:
#   powershell -ExecutionPolicy Bypass -File setup-android.ps1
# Optional (TV on network): -TvAddress "192.168.1.50:5555"

param(
  [string]$ProjectRoot = (Join-Path $HOME "Documents\tv-bridge"),
  [string]$TvAddress = "",
  [switch]$SkipStudioInstall
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

function Test-Command($name) {
  return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Ensure-Winget {
  if (-not (Test-Command winget)) {
    throw "winget is required (Windows 10/11 App Installer). Install from Microsoft Store: App Installer"
  }
}

function Install-IfMissing($wingetId, $label) {
  $list = winget list --id $wingetId --accept-source-agreements 2>$null
  if ($LASTEXITCODE -ne 0 -or $list -notmatch [regex]::Escape($wingetId)) {
    Write-Host "Installing $label..." -ForegroundColor Yellow
    winget install -e --id $wingetId --accept-package-agreements --accept-source-agreements
  } else {
    Write-Host "$label already installed." -ForegroundColor Green
  }
}

function Get-AndroidSdkRoot {
  if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
  if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }
  $default = Join-Path $env:LOCALAPPDATA "Android\Sdk"
  if (Test-Path $default) { return $default }
  return $default
}

function Get-JavaHome {
  $candidates = @(
    $env:JAVA_HOME,
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Microsoft\jdk-17*",
    "C:\Program Files\Eclipse Adoptium\jdk-17*"
  )
  foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    if ($c -like "**") {
      $found = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($found) { return $found.FullName }
    }
  }
  return $null
}

function Ensure-AndroidCmdlineTools($SdkRoot) {
  $sdkmanager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
  if (Test-Path $sdkmanager) { return $sdkmanager }

  Write-Host "Downloading Android command-line tools..." -ForegroundColor Yellow
  $toolsDir = Join-Path $SdkRoot "cmdline-tools"
  $zipPath = Join-Path $env:TEMP "cmdline-tools-win.zip"
  $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
  New-Item -ItemType Directory -Force -Path (Join-Path $toolsDir "latest") | Out-Null
  Expand-Archive -Path $zipPath -DestinationPath $env:TEMP -Force
  Copy-Item -Path (Join-Path $env:TEMP "cmdline-tools\*") -Destination (Join-Path $toolsDir "latest") -Recurse -Force
  Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
  if (-not (Test-Path $sdkmanager)) { throw "sdkmanager not found after cmdline-tools install." }
  return $sdkmanager
}

function Ensure-AndroidSdkPackages($SdkRoot) {
  $sdkmanager = Ensure-AndroidCmdlineTools $SdkRoot
  Write-Host "Accepting SDK licenses and installing packages (first time may take several minutes)..." -ForegroundColor Yellow
  $yes = ("y`n" * 40)
  $yes | & $sdkmanager --sdk_root=$SdkRoot --licenses | Out-Null
  & $sdkmanager --sdk_root=$SdkRoot "platform-tools" "platforms;android-35" "build-tools;35.0.0" | Out-Null
}

function Write-LocalProperties($ModuleDir, $SdkRoot) {
  $sdkPath = $SdkRoot -replace '\\', '/'
  Set-Content -Path (Join-Path $ModuleDir "local.properties") -Value "sdk.dir=$sdkPath`n" -Encoding UTF8
}

function Invoke-Gradle($ModuleDir, $Task) {
  Set-Location $ModuleDir
  if (-not (Test-Path ".\gradlew.bat")) { throw "gradlew.bat not found in $ModuleDir" }
  cmd /c ".\gradlew.bat $Task"
  if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $Task in $ModuleDir" }
}

function Install-Apk($ApkPath, $DeviceSelector) {
  if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }
  $adb = Get-Command adb -ErrorAction SilentlyContinue
  if (-not $adb) { throw "adb not in PATH. Reopen PowerShell after Platform Tools install." }
  if ($DeviceSelector) {
    & adb -s $DeviceSelector install -r $ApkPath
  } else {
    & adb install -r $ApkPath
  }
  if ($LASTEXITCODE -ne 0) { throw "adb install failed for $ApkPath" }
  Write-Host "Installed: $ApkPath" -ForegroundColor Green
}

# ── Main ────────────────────────────────────────────────────────────────

Write-Step "0) Check project folder"
if (-not (Test-Path (Join-Path $ProjectRoot "android-tv-receiver\gradlew.bat"))) {
  throw "TV-Bridge not found at: $ProjectRoot`nClone first (see INSTALLATION.md Windows script)."
}
Write-Host "Project: $ProjectRoot" -ForegroundColor Green

if (-not $SkipStudioInstall) {
  Write-Step "1) Install JDK, Android Studio, and Platform Tools (winget)"
  Ensure-Winget
  Install-IfMissing "Microsoft.OpenJDK.17" "Microsoft OpenJDK 17"
  Install-IfMissing "Google.AndroidStudio" "Android Studio"
  Install-IfMissing "Google.PlatformTools" "Android Platform Tools (adb)"
  Write-Host "If winget asked for admin, approve the prompts." -ForegroundColor Yellow
  Write-Host "Reopen PowerShell if adb/java were just installed." -ForegroundColor Yellow
}

Write-Step "2) Configure JAVA_HOME and ANDROID_HOME"
$env:JAVA_HOME = Get-JavaHome
$env:ANDROID_HOME = Get-AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not $env:JAVA_HOME) { throw "JAVA_HOME not found. Install OpenJDK 17 or Android Studio." }
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
New-Item -ItemType Directory -Force -Path $env:ANDROID_HOME | Out-Null

Write-Step "3) Android SDK packages"
Ensure-AndroidSdkPackages $env:ANDROID_HOME

Write-Step "4) Write local.properties"
Write-LocalProperties (Join-Path $ProjectRoot "android-tv-receiver") $env:ANDROID_HOME
Write-LocalProperties (Join-Path $ProjectRoot "android-phone-sender") $env:ANDROID_HOME

Write-Step "5) Build TV receiver (debug APK)"
Invoke-Gradle (Join-Path $ProjectRoot "android-tv-receiver") "assembleDebug"
$TvApk = Join-Path $ProjectRoot "android-tv-receiver\app\build\outputs\apk\debug\app-debug.apk"

Write-Step "6) Build phone sender (release APK — auto-served at /download when server runs)"
Invoke-Gradle (Join-Path $ProjectRoot "android-phone-sender") "assembleRelease"
$PhoneApk = Join-Path $ProjectRoot "android-phone-sender\app\build\outputs\apk\release\app-release.apk"

Write-Step "7) Install to devices (adb)"
& adb start-server | Out-Null

if ($TvAddress) {
  Write-Host "Connecting to TV: $TvAddress" -ForegroundColor Yellow
  & adb connect $TvAddress
  Install-Apk $TvApk $TvAddress
} else {
  $devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "`tdevice$" }
  if ($devices.Count -eq 0) {
    Write-Host "No USB/network device for TV. Set -TvAddress 192.168.x.x:5555 or enable USB debugging." -ForegroundColor Yellow
  } else {
    Write-Host "Installing TV receiver on: $($devices[0])" -ForegroundColor Yellow
    Install-Apk $TvApk $null
  }
}

$phoneDevices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "`tdevice$" -and $_ -notmatch ":5555" }
if ($phoneDevices.Count -gt 0) {
  Write-Host "Installing phone sender on connected phone..." -ForegroundColor Yellow
  Install-Apk $PhoneApk $null
} else {
  Write-Host "Phone sender APK ready. USB phone not detected — install from:" -ForegroundColor Yellow
  Write-Host "  https://<host-ip>:3443/download" -ForegroundColor Cyan
}

Write-Step "Done"
Write-Host "TV APK:    $TvApk"
Write-Host "Phone APK: $PhoneApk (served by signaling-server at /downloads/tv-bridge-emisor.apk)"
Write-Host "Start server: cd signaling-server; npm start" -ForegroundColor Cyan
