# TV-Bridge Installation Guide

Complete setup for the signaling server, Android TV receiver, and Android phone sender. All devices must be on the **same local network** (Wi‑Fi or Ethernet).

## What you need

| Role | Hardware / software |
|------|---------------------|
| **Host** | PC or Mac with Node.js 18+ (runs the signaling server) |
| **Display** | Android TV or Google TV (API 24+) |
| **Sender (optional)** | PC browser and/or Android phone |

## 1. Get the project

```bash
git clone https://github.com/miguelcobona-cloud/tv-bridge.git
cd tv-bridge
```

Or download the ZIP from GitHub and extract it.

---

## 2. Install the signaling server (host PC)

The signaling server is the only component that must run on a computer. It serves the web UI and coordinates WebRTC connections.

### Windows (PowerShell)

```powershell
cd signaling-server
npm install
npm start
```

### macOS / Linux

```bash
cd signaling-server
npm install
npm start
```

You should see:

```text
TV-Bridge signaling server listening on http://localhost:3000
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

## 3. Install the Android TV receiver

The TV app registers with the signaling server and displays the incoming WebRTC stream.

### Option A — Build from source (recommended for developers)

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug 2024.2+).
2. **File → Open** → select the `android-tv-receiver/` folder.
3. Wait for Gradle sync.
4. Connect an Android TV device via ADB, or create an **Android TV (1080p)** emulator.
5. Click **Run** to install the debug build.

See [android-tv-receiver/README.md](android-tv-receiver/README.md) for module details.

### Option B — Sideload a release APK

If a release APK is published under [GitHub Releases](https://github.com/miguelcobona-cloud/tv-bridge/releases), copy it to a USB drive or use `adb install`:

```bash
adb connect <tv-ip>:5555
adb install tv-bridge-receiver.apk
```

Enable **Install unknown apps** on the TV if prompted.

### Configure the TV app

1. Open **TV-Bridge** on the TV.
2. Enter the **host IP** (the PC running `npm start`), e.g. `192.168.1.100`.
3. Enter a **display name** (e.g. `Living Room`) — senders will see this name in the list.
4. Select **Connect and wait for stream**.
5. Status should show **Connected — waiting for sender**.

---

## 4. Install the Android phone sender (optional)

Use this when you want to mirror your **phone screen** to the TV (with audio support).

### Option A — Download from the host (after building the APK)

1. Build the release APK on a machine with Android Studio:

   ```bash
   cd android-phone-sender
   ./gradlew assembleRelease
   ```

   On Windows: `.\gradlew.bat assembleRelease`

2. With the signaling server running, open on your phone:

   `https://<host-ip>:3443/download`

3. Install the APK (allow unknown sources if asked).

### Option B — Build and install via USB

1. Open `android-phone-sender/` in Android Studio.
2. Run on a physical phone (screen capture does not work well on emulators).
3. Or install the release APK with `adb install app/build/outputs/apk/release/app-release.apk`.

Signing uses the community keystore documented in [android-phone-sender/signing/README.md](android-phone-sender/signing/README.md).

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

---

## Next steps

- [README.md](README.md) — overview and architecture
- [CONTRIBUTING.md](CONTRIBUTING.md) — development setup
- [android-tv-receiver/README.md](android-tv-receiver/README.md) — TV app internals
- [android-phone-sender/README.md](android-phone-sender/README.md) — phone sender internals
