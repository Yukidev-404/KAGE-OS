# KAGE//OS

**A touch-controlled Android terminal with a rotary tool selector.**

KAGE//OS is an early-stage Android terminal client designed to connect to the user's existing Termux/Debian environment through a local bridge. The goal is to provide a mobile terminal experience without bundling a separate, disconnected Linux environment.

> **Status: Prototype.** This project is under active development. Build success does not guarantee that all terminal interactions or bridge workflows work correctly on every device.

## Current features

- Native Android app built with Kotlin and Jetpack Compose.
- Rotary selector for RECON, WEB, REVERSE, CODEX, OPENCODE, and SYSTEM action groups.
- Portrait and landscape layouts.
- Terminal rendering integration using the Termux `terminal-emulator` and `terminal-view` modules.
- Socket-backed terminal bridge integration using newline-delimited JSON packets.
- Local development build through Gradle.

Some interactive terminal features, keyboard mappings, ANSI/xterm behavior, lifecycle handling, and device-specific behavior may still need improvement.

## How it works

- **Android client:** Kotlin, Jetpack Compose, and the terminal view.
- **Shell environment:** the user's existing Termux and Debian PRoot installation.
- **Bridge:** a local process in Termux communicates with the Android client over loopback.

Android's application sandbox means the app does not directly execute binaries from Termux's private directories. The bridge provides the connection to the existing environment.

## Development setup

### Requirements

- Android Studio or a compatible Android development environment
- JDK 17
- Android SDK with platform `android-35` and build-tools `35.0.0`
- A compatible Android device or emulator for testing

### Build

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```sh
./gradlew assembleDebug
```

The Gradle wrapper downloads its configured Gradle version on first use. The debug APK is normally generated under `app/build/outputs/apk/debug/`.

### Bridge

Follow the bridge-specific instructions in [bridge/README.md](bridge/README.md), if present in your checkout. Keep the bridge bound to loopback (`127.0.0.1`) and do not expose it to a LAN or the public internet.

## Safety notes

- Use cybersecurity tools only on systems you own or are explicitly authorized to test.
- Commands entered in the terminal run with the permissions of the connected Termux/Debian user.
- Do not treat PRoot as a security boundary or run the bridge with unnecessary privileges.
- Never commit API keys, passwords, access tokens, signing keys, or other secrets.
- This project is a prototype and has not been independently security-audited.

## Third-party code

This repository includes or adapts the Termux `terminal-emulator` and `terminal-view` modules. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the applicable license notices in those modules for attribution and license details.

## Roadmap

- Improve terminal keyboard and ANSI/xterm compatibility.
- Improve connection, resize, and session lifecycle handling.
- Test bridge behavior and interactive applications on real devices.
- Improve automated builds and release packaging.

## License

KAGE//OS is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
