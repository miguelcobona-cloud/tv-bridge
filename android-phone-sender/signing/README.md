# Release signing (open source / sideload)

Development keystore for **community builds only** — install TV-Bridge Sender without Google Play.

| Field | Value |
|-------|--------|
| File | `oss-release.keystore` |
| Alias | `tvbridge` |
| Password | `tvbridge-oss` |

**Do not use this key for commercial production apps.** It is included so anyone can build and sideload the release APK locally.

Build release APK:

```bash
cd android-phone-sender
./gradlew assembleRelease
```

On Windows:

```powershell
cd android-phone-sender
.\gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`
