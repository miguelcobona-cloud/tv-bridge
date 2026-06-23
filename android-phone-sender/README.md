# TV-Bridge Phone Sender (Android)

Native **Kotlin + Jetpack Compose** app that captures the phone screen and streams it to an Android TV via WebRTC.

## Requirements

- Android Studio Ladybug (2024.2+) or newer
- Android SDK 35
- Android phone or emulator (API 24+)
- TV-Bridge signaling server running on the same LAN

## Open in Android Studio

1. **File → Open** and select the `android-phone-sender/` folder.
2. Wait for Gradle sync.
3. Run on a physical device (screen capture requires a real device for best results).

## Usage

1. Start the signaling server on your PC (`cd signaling-server && npm start`).
2. Open **TV-Bridge Sender** on your phone.
3. Enter the host address (or scan a connect link / QR code if provided by the server UI).
4. Pick a TV from the live list and start casting.
5. Grant **screen capture** permission when prompted.

The app registers as a web emitter (`register-web`) and negotiates WebRTC directly with the selected TV.

## Module structure

```
app/src/main/java/com/tvbridge/sender/
├── data/ServerPreferences.kt
├── signaling/EmitterSignalingClient.kt
├── webrtc/WebRtcSenderSession.kt
├── service/ProjectionForegroundService.kt
├── ui/                             # Setup, TV list, streaming screens
└── SenderViewModel.kt
```

## Build release APK (sideload)

Community release builds are signed with the open keystore in [`signing/`](signing/README.md):

```bash
cd android-phone-sender
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

The signaling server can also serve this APK at `/download` when the file is present after a release build.

## License

[MIT](../LICENSE)
