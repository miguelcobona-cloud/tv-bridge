# Contributing to TV-Bridge

Thank you for your interest in contributing! TV-Bridge is an open-source,
self-hosted screen-mirroring stack for Android TV.

## Getting started

1. Fork the repository and clone your fork.
2. Install [Node.js](https://nodejs.org/) 18+ for the signaling server.
3. Install [Android Studio](https://developer.android.com/studio) for the Android modules.
4. Start the signaling server:

   ```bash
   cd signaling-server
   npm install
   npm start
   ```

5. Open `android-tv-receiver/` or `android-phone-sender/` in Android Studio and run on a device or emulator.

## What to work on

* Bug fixes and reliability improvements on local networks
* Documentation and translations
* UX improvements for TV D-Pad navigation and mobile senders
* Performance tuning for WebRTC on Android TV hardware

Check [GitHub Issues](https://github.com/miguelcobona-cloud/tv-bridge/issues) for open tasks.

## Pull request guidelines

* Keep changes focused — one logical change per PR.
* Do not commit `node_modules/`, Gradle `build/` output, or local TLS certificates.
* Update relevant README sections when behavior or setup steps change.
* Describe how you tested the change (devices, network setup, browsers).

## Code style

* **Kotlin:** follow existing patterns in each Android module; keep imports at the top of files.
* **JavaScript:** plain ES modules in `signaling-server/public/`; no heavy frontend frameworks.
* **Node.js:** native `http`/`https` modules only — no Express or Socket.io in the signaling server.

## Security

If you discover a security vulnerability, please open a private security advisory on GitHub rather than filing a public issue.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
