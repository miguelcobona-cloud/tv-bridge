# TV-Bridge — Architecture Specification

Open-source screen mirroring from a PC or phone to Android TV over low-latency WebRTC on the local network.

## Goals

- Real-time screen mirroring at zero recurring cost
- Offline-first on LAN (self-hosted signaling)
- Simple deployment via terminal or PowerShell (`npm install`, `npm start`)

## System flow (Option B)

1. **Signaling server** — Local Node.js process with WebSockets and static web UI.
2. **Android TV receivers** — On launch, connect to the host WebSocket and register a display name (e.g. `Living Room`).
3. **Senders (PC browser / phone)** — Open the web UI or native sender app, see TVs in real time, select one, and start casting. WebRTC offer/answer and ICE candidates are exchanged via the server; **video is P2P** to the TV.

## Modules

### `signaling-server/`

- `package.json` — `npm start` runs `server.js`; WebSocket dependency: `ws` only (no Express/Socket.io).
- `server.js` — HTTP on port 3000, serves `public/`, integrated WebSocket server, in-memory TV registry, transparent WebRTC signaling relay, live TV list updates on disconnect.
- `public/` — Vanilla HTML/CSS/JS sender UI with `getDisplayMedia()` and dynamic TV list.

### `android-tv-receiver/`

- Kotlin + Jetpack Compose for TV.
- Gradle with OkHttp (WebSocket) and WebRTC (`SurfaceViewRenderer`).
- Setup screen: D-Pad friendly server IP input, persisted locally.
- Playback screen: fullscreen incoming WebRTC video.

### `android-phone-sender/` (extension)

- Native Android sender with screen capture and audio for phone → TV casting.

## Quality bar

- Modular, commented WebRTC code paths
- Root `README.md` with architecture and quick start
- Async, decoupled signaling so the server is not a media bottleneck
