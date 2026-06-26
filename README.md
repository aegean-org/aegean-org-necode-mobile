# NeCode Mobile

NeCode Mobile is the GPLv3 Android companion app for NeCode. It is based on the Litter mobile client and focuses on one workflow: using a phone to control a NeCode session running on the user's own computer.

See [OPEN_SOURCE.md](OPEN_SOURCE.md) for the open-source boundary across the mobile app, daemon, and NeCode CLI.

## Current Architecture

```text
Android app
  -> self-hosted iroh relay
  -> local NeCode mobile daemon (Alleycat)
  -> NeCode bridge
  -> local NeCode CLI
```

The relay only provides connectivity. Project files, command execution, model configuration, and agent state stay on the user's computer.

## Repository Layout

```text
apps/android/              Android app
apps/ios/                  Upstream iOS app, not productized for NeCode yet
services/kittylitter/      Local daemon wrapper
shared/rust-bridge/        Shared Rust mobile client and UniFFI bindings
shared/third_party/codex/  Upstream Codex submodule
patches/codex/             Local patch set applied during builds
tools/scripts/             Build and maintenance scripts
```

## Android Development

```powershell
cd D:\project\litter\apps\android
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Install a debug build:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r D:\project\litter\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

## Pairing

For normal NeCode users, start the local daemon through NeCode CLI:

```powershell
necode mobile serve
```

Generate a QR pairing payload:

```powershell
necode mobile qr
```

Scan the QR code from the NeCode Android app and select the `necode` agent.

For local daemon development, the wrapper can still be run directly:

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- serve
cargo run -- pair --qr
```

## Documentation

See [NECODE_MOBILE_SETUP.md](NECODE_MOBILE_SETUP.md) for the current end-to-end setup and troubleshooting notes.

## License

This mobile app is derived from Litter and remains licensed under the GNU General Public License version 3 with the additional permission under GPLv3 section 7 for Apple App Store and Google Play distribution. See [LICENSE](LICENSE) and [OPEN_SOURCE.md](OPEN_SOURCE.md).
