# TV-Bridge Receiver (Android TV)

Native **Kotlin + Jetpack Compose for TV** app that acts as the **WebRTC receiver** in the TV-Bridge stack.

## Requirements

- Android Studio Ladybug (2024.2+) or newer
- Android SDK 35
- Android TV device or emulator (API 24+)
- TV-Bridge signaling server running (`signaling-server/`)

## Open in Android Studio

1. **File → Open** and select the `android-tv-receiver/` folder.
2. Wait for Gradle to sync dependencies (`google-webrtc`, OkHttp, Compose for TV).
3. Connect an Android TV or create an **Android TV (1080p)** AVD.
4. Run the app.

## Setup on the TV

1. Enter the **host IP** where `npm start` is running (port `3000` by default).
2. Choose a **display name** (e.g. *Living Room*) — it appears in the sender UI.
3. Tap **Connect and wait for stream**.
4. From a browser (`http://<host-ip>:3000`) or the phone sender app, select this TV and start casting.

## Module structure

```
app/src/main/java/com/tvbridge/receiver/
├── data/ServerPreferences.kt       # Persistent server IP and TV name
├── signaling/SignalingClient.kt    # OkHttp WebSocket (server.js protocol)
├── webrtc/WebRtcReceiverSession.kt # WebRTC answerer + SurfaceViewRenderer
├── ui/                             # Compose for TV (Setup, Waiting, Playback)
└── ReceiverViewModel.kt            # Signaling + WebRTC orchestration
```

## Signaling protocol

Compatible with `signaling-server/server.js`:

| Direction | Message | Description |
|-----------|---------|-------------|
| TV → server | `register-tv` | Register display name |
| Server → TV | `registered` | Returns unique `id` |
| Sender → TV | `signal` (offer / ICE) | Starts WebRTC negotiation |
| TV → sender | `signal` (answer / ICE) | P2P reply |

**Video flows directly P2P** between the sender and the TV; the server only exchanges SDP and ICE candidates.

## Key dependencies

- `org.webrtc:google-webrtc` — rendering via `SurfaceViewRenderer`
- `com.squareup.okhttp3:okhttp` — WebSocket client
- `androidx.tv:tv-material` — D-Pad optimized TV UI

## Network notes

- `android:usesCleartextTraffic="true"` allows `ws://` on LAN without TLS.
- The TV and the signaling host must be on the same local network.

## License

[MIT](../LICENSE)
