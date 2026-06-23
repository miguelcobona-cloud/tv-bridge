# TV-Bridge Installation Guide

Complete setup for the signaling server, Android TV receiver, and Android phone sender. All devices must be on the **same local network** (Wi‑Fi or Ethernet).

## What you need

| Role | Hardware / software |
|------|---------------------|
| **Host** | PC with **Node.js 18+**, **Git**, and (for Android apps) **Android Studio** / SDK — installed automatically on Windows via script |
| **Display** | Android TV or Google TV (API 24+) |
| **Sender (optional)** | PC browser and/or Android phone |

---

## Windows — full install (copy & paste)

Open **PowerShell** (not CMD). Select **all lines** below, paste, press **Enter**.  
Each step checks the path before continuing — you should not need to type `cd` by hand.

```powershell
# ── 0) Verify Node.js, npm, and Git ─────────────────────────────
Write-Host "`n=== Checking tools ===" -ForegroundColor Cyan
node --version
npm --version
git --version
if ($LASTEXITCODE -ne 0) { throw "Install Node.js from https://nodejs.org and Git from https://git-scm.com then open a NEW PowerShell window." }

# ── 1) Project folder (default: Documents\tv-bridge) ───────────
# To use another drive/folder, change ONLY the next line, e.g. "D:\Apps\tv-bridge"
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge"
Write-Host "`nProject folder: $ProjectRoot" -ForegroundColor Cyan

# ── 2) Download repo if signaling-server is missing ─────────────
$ServerDir = Join-Path $ProjectRoot "signaling-server"
$PackageJson = Join-Path $ServerDir "package.json"

if (-not (Test-Path $PackageJson)) {
  Write-Host "Downloading TV-Bridge from GitHub..." -ForegroundColor Yellow
  $Parent = Split-Path $ProjectRoot -Parent
  if (-not (Test-Path $Parent)) { New-Item -ItemType Directory -Force -Path $Parent | Out-Null }
  if (Test-Path (Join-Path $ProjectRoot ".git")) {
    Set-Location $ProjectRoot
    git pull
  } elseif (Test-Path $ProjectRoot) {
    throw "Folder exists but is not a TV-Bridge clone: $ProjectRoot`nDelete it or change `$ProjectRoot to another path."
  } else {
    git clone https://github.com/miguelcobona-cloud/tv-bridge.git $ProjectRoot
  }
}

if (-not (Test-Path $PackageJson)) {
  throw "ERROR: package.json not found at:`n  $PackageJson`nCheck `$ProjectRoot or clone manually."
}

# ── 3) Enter signaling-server (guided path, no manual cd) ───────
Set-Location $ServerDir
Write-Host "`nCurrent folder: $(Get-Location)" -ForegroundColor Green
Write-Host "package.json found: $(Test-Path package.json)" -ForegroundColor Green

# ── 4) Install dependencies and start server ────────────────────
Write-Host "`n=== npm install ===" -ForegroundColor Cyan
npm install
if ($LASTEXITCODE -ne 0) { throw "npm install failed." }

Write-Host "`n=== npm start (Ctrl+C to stop) ===" -ForegroundColor Cyan
npm start
```

When it works you will see:

```text
TV-Bridge signaling server listening on http://localhost:3000
```

Open in the browser: **http://localhost:3000**

### Already have the project somewhere else?

Run this to **find** `signaling-server` on your PC, then start the server:

```powershell
# Search common locations (may take a few seconds)
$found = @(
  Get-ChildItem -Path $HOME\Documents, $HOME\Downloads, $HOME\Desktop -Recurse -Filter "package.json" -ErrorAction SilentlyContinue |
    Where-Object { $_.DirectoryName -match "signaling-server$" }
) | Select-Object -First 1

if (-not $found) {
  Write-Host "Not found under Documents/Downloads/Desktop. Use the full script above to clone to Documents\tv-bridge."
} else {
  $ServerDir = $found.DirectoryName
  Write-Host "Found: $ServerDir" -ForegroundColor Green
  Set-Location $ServerDir
  npm install
  npm start
}
```

### ZIP download instead of Git?

1. Download ZIP from https://github.com/miguelcobona-cloud/tv-bridge/archive/refs/heads/main.zip  
2. Extract it (folder is often `tv-bridge-main`).  
3. Run (adjust path if you extracted elsewhere):

```powershell
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge-main"
$ServerDir = Join-Path $ProjectRoot "signaling-server"
Set-Location $ServerDir
if (-not (Test-Path package.json)) { throw "Wrong folder. List contents: $(Get-ChildItem $ProjectRoot)" }
npm install
npm start
```

---

## 1. Get the project (macOS / Linux)

```bash
git clone https://github.com/miguelcobona-cloud/tv-bridge.git
cd tv-bridge
ls signaling-server/package.json   # must exist
```

---

## 2. Install the signaling server (macOS / Linux)

```bash
cd "$(dirname "$0")/.." 2>/dev/null || true
cd ~/Documents/tv-bridge/signaling-server   # or your clone path
test -f package.json || { echo "ERROR: not in signaling-server"; exit 1; }
node --version && npm --version
npm install
npm start
```

Or from the repo root:

```bash
cd tv-bridge
cd signaling-server && npm install && npm start
```

### Endpoints

| URL | Use case |
|-----|----------|
| `http://<host-ip>:3000` | Desktop browsers (screen share from PC) |
| `https://<host-ip>:3443` | Mobile browsers and some Android WebViews (HTTPS required) |

On first start, a **self-signed TLS certificate** is created in `signaling-server/certs/` (this folder is gitignored).

### Find your host IP

**Windows:**

```powershell
ipconfig
```

Look for **IPv4 Address** on your active Wi‑Fi or Ethernet adapter (e.g. `192.168.1.100`).

**macOS / Linux:**

```bash
ip addr show
# or
ifconfig
```

### Firewall

Allow inbound TCP on ports **3000** and **3443** on the host machine so TVs and phones on the LAN can connect.

**Windows (PowerShell, run as Administrator):**

```powershell
New-NetFirewallRule -DisplayName "TV-Bridge HTTP" -Direction Inbound -Protocol TCP -LocalPort 3000 -Action Allow
New-NetFirewallRule -DisplayName "TV-Bridge HTTPS" -Direction Inbound -Protocol TCP -LocalPort 3443 -Action Allow
```

### Keep the server running

Leave the terminal open while casting. Stop with `Ctrl+C`.

---

## Windows — Android apps (automatic install & deploy)

Builds both APKs and installs them to your **TV** and **phone** without opening Android Studio manually.

**What this does:**

| Step | Action |
|------|--------|
| 1 | Installs **OpenJDK 17**, **Android Studio**, and **adb** via `winget` (if missing) |
| 2 | Downloads Android SDK command-line tools and required packages |
| 3 | Builds **TV receiver** (debug) and **phone sender** (release) |
| 4 | Installs to TV/phone with `adb` (release APK is also served at `/download` when the server runs) |

### Before you run (one-time on devices)

**Android TV / Google TV**

1. **Settings → Device preferences → About →** click **Build** 7 times → Developer options.
2. **Developer options → USB debugging** ON.
3. **Network debugging** (or **ADB over network**) ON — note the IP shown, e.g. `192.168.1.50:5555`.

**Android phone (optional sender)**

1. Enable **Developer options** and **USB debugging**.
2. Connect USB, accept **Allow USB debugging** on the phone.

### Run the script (recommended)

Open **PowerShell** (first time may need **Run as Administrator** for winget).  
Replace `192.168.1.50:5555` with your TV’s network debugging address.

```powershell
# Same project path as the signaling-server script
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge"

# Clone project first if you have not (see "Windows — full install" above)
if (-not (Test-Path (Join-Path $ProjectRoot "scripts\windows\setup-android.ps1"))) {
  git clone https://github.com/miguelcobona-cloud/tv-bridge.git $ProjectRoot
}

Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
& (Join-Path $ProjectRoot "scripts\windows\setup-android.ps1") -TvAddress "192.168.1.50:5555"
```

**Without TV IP** (USB TV box or install APKs only):

```powershell
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
& (Join-Path $ProjectRoot "scripts\windows\setup-android.ps1")
```

**Skip Android Studio install** (already installed):

```powershell
& (Join-Path $ProjectRoot "scripts\windows\setup-android.ps1") -TvAddress "192.168.1.50:5555" -SkipStudioInstall
```

First run can take **15–30 minutes** (SDK download + Gradle). Next builds are much faster.

### All-in-one: server + Android (two terminals)

**Terminal 1 — signaling server** (keep open):

```powershell
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge"
Set-Location (Join-Path $ProjectRoot "signaling-server")
npm install
npm start
```

**Terminal 2 — build & install apps** (while server runs in terminal 1):

```powershell
$ProjectRoot = Join-Path $HOME "Documents\tv-bridge"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
& (Join-Path $ProjectRoot "scripts\windows\setup-android.ps1") -TvAddress "192.168.1.50:5555"
```

After install, open **TV-Bridge** on the TV and **TV-Bridge Sender** on the phone. The phone APK is also at:

`https://<host-ip>:3443/download`

---

## 3. Install the Android TV receiver

> **Windows:** use the [automatic script](#windows--android-apps-automatic-install--deploy) above instead of manual steps.

### macOS / Linux — command line

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"   # Linux: ~/Android/Sdk
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null)}"

cd ~/Documents/tv-bridge/android-tv-receiver
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug

adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [android-tv-receiver/README.md](android-tv-receiver/README.md) for module details.

### Configure the TV app

1. Open **TV-Bridge** on the TV.
2. Enter the **host IP** (the PC running `npm start`), e.g. `192.168.1.100`.
3. Enter a **display name** (e.g. `Living Room`) — senders will see this name in the list.
4. Select **Connect and wait for stream**.
5. Status should show **Connected — waiting for sender**.

---

## 4. Install the Android phone sender (optional)

> **Windows:** the [automatic script](#windows--android-apps-automatic-install--deploy) builds the release APK and installs via USB, or serves it from the signaling server.

### macOS / Linux

```bash
cd ~/Documents/tv-bridge/android-phone-sender
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or download from the host: `https://<host-ip>:3443/download`

Signing uses the community keystore in [android-phone-sender/signing/README.md](android-phone-sender/signing/README.md).

### Configure the phone app

1. Open **TV-Bridge Sender**.
2. Enter the same host address as the TV (`192.168.1.100` or a connect link / QR from the server UI).
3. Pick the TV from the list and start casting.
4. Grant **screen capture** permission when Android asks.

---

## 5. Cast from a PC browser

1. Ensure the signaling server is running and the TV is connected (waiting state).
2. On the same LAN, open Chrome, Edge, or Firefox:
   - `http://<host-ip>:3000`
3. The TV should appear under **Available TVs**.
4. Click the TV → choose screen or window → confirm sharing.
5. Video flows **directly** to the TV (P2P). The server only relays connection setup messages.

> **Note:** `getDisplayMedia()` works best on desktop browsers. Mobile browsers have limited screen-capture support; use the Android sender app on phones instead.

---

## 6. Trust the HTTPS certificate (phones)

When using `https://<host-ip>:3443`, the browser will warn about the self-signed certificate.

- **Android Chrome:** Advanced → Proceed (unsafe) — acceptable on your home LAN only.
- **Desktop:** Accept the security exception for your local IP.

To regenerate certificates after a network change, delete `signaling-server/certs/` and restart `npm start`.

---

## 7. Verify everything works

| Step | Expected result |
|------|-----------------|
| `npm start` on host | Console shows HTTP/HTTPS URLs |
| Open `http://localhost:3000` | Web UI loads, status **Connected** |
| TV app connected | TV name appears in the web list |
| Start cast from browser | TV switches to fullscreen video |
| Stop cast | TV returns to waiting screen |

---

## Troubleshooting

### `cd` path not found OR `npm` not recognized (Windows)

**Use the [full copy-paste script](#windows--full-install-copy--paste) at the top of this guide.** It clones to `Documents\tv-bridge`, checks every folder, and runs `npm` without manual `cd`.

If `npm` still fails after Node.js is installed:

```powershell
node --version
npm --version
```

Close PowerShell, open a **new** window, run the full script again. Reinstall Node.js from [nodejs.org](https://nodejs.org) with **Add to PATH** checked if needed.

### TV does not appear in the list

- Confirm TV and host are on the **same subnet**.
- Check the IP entered on the TV matches the host running `npm start`.
- Restart the TV app and refresh the browser page.
- Verify firewall rules for ports 3000 and 3443.

### Web page shows “Not Found”

- Use the full path: `http://<host-ip>:3000/` (trailing slash optional).
- Ensure you are in the `signaling-server` directory when running `npm start`.

### WebRTC connects but no video

- Some routers block UDP between Wi‑Fi clients (AP isolation). Disable **client isolation** on the router.
- Try Ethernet for the host or TV if Wi‑Fi is unstable.
- Check that only one sender is casting to the TV at a time.

### Phone sender cannot connect over HTTPS

- Use port **3443**, not 3000.
- Accept the self-signed certificate warning.
- Ensure the release APK was built so `/download` can serve it.

### Port 3000 already in use

**Windows:**

```powershell
Get-NetTCPConnection -LocalPort 3000 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

Then run `npm start` again.

### Android build or adb failed (Windows)

1. Run PowerShell **as Administrator** once for winget installs.
2. Close and reopen PowerShell after installing JDK / Platform Tools.
3. Enable **Network debugging** on the TV and pass the correct address:

   ```powershell
   adb connect 192.168.1.50:5555
   adb devices
   ```

4. Re-run:

   ```powershell
   & (Join-Path $HOME "Documents\tv-bridge\scripts\windows\setup-android.ps1") -TvAddress "192.168.1.50:5555" -SkipStudioInstall
   ```

5. If Gradle says SDK missing, delete `%LOCALAPPDATA%\Android\Sdk` and run the script again (it will re-download tools).

---

## Next steps

- [README.md](README.md) — overview and architecture
- [CONTRIBUTING.md](CONTRIBUTING.md) — development setup
- [android-tv-receiver/README.md](android-tv-receiver/README.md) — TV app internals
- [android-phone-sender/README.md](android-phone-sender/README.md) — phone sender internals
