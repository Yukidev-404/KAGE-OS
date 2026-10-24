# Terminal Dial

**Touch-controlled Android terminal with a rotary cybersecurity tool selector.**

Terminal Dial is a native Android app for controlling the user's existing Termux/Debian environment through a local bridge. It is not intended to fake a shell or bundle a second, disconnected Linux environment.

## Current prototype

- Kotlin + Jetpack Compose Android app scaffold.
- Portrait layout: terminal pane above the rotary selector.
- Landscape layout: terminal on the left, selector on the right.
- Drag to rotate through RECON, WEB, REVERSE, CODEX, OPENCODE, and SYSTEM; tap the center to request the selected action.
- A Termux-side Python PTY bridge that binds to `127.0.0.1:8765`, launches the existing Debian PRoot shell, and keeps the shell alive while the app disconnects/reconnects as long as the bridge process remains running.
- The app and bridge use newline-delimited JSON packets.

**This is an early prototype, not a release-ready terminal emulator.** The output pane is currently plain text; ANSI/xterm cursor handling, full interactive key mapping, robust background-service lifecycle, build automation, and on-device validation remain to be done. In particular, interactive TUIs such as Codex/OpenCode may not render or accept all keys correctly until a proper terminal emulator is integrated.

## Architecture

- **Android client:** Kotlin, Jetpack Compose, custom rotary selector.
- **Execution environment:** the user's existing Termux + Debian PRoot installation.
- **Local bridge:** `bridge/terminal_dial_bridge.py`, run from Termux (outside Debian).
- **PTY session:** the bridge launches `proot-distro login debian --isolated -- /bin/bash --login`.

Android apps are sandboxed. The Android client cannot directly execute binaries in Termux's private directories; the bridge is an intentional integration point. The bridge is not a security boundary against other processes running under the same Android/Termux user.

## Quick start (development)

1. Open this repository in Android Studio and let Gradle sync.
2. Install Python in Termux if needed: `pkg install python`.
3. Copy `bridge/terminal_dial_bridge.py` onto the phone, for example to `~/terminal_dial_bridge.py` in Termux.
4. Start it from the **Termux shell**, not inside Debian: `python ~/terminal_dial_bridge.py`.
5. Run the Android app on the same device and tap **CONNECT**.

The current repository does not yet include a Gradle wrapper or a signed/release APK. The first on-device run is needed to validate the Gradle setup, socket connection, PTY behavior, and layout on the target phone.

See [bridge/README.md](bridge/README.md) for protocol details and limitations.

## Safety defaults

- Bind the bridge to `127.0.0.1` only; never expose it to a LAN or the internet.
- Do not run as root or claim PRoot is a security boundary.
- Tool-group launch actions are fixed and allowlisted, although commands typed into the terminal intentionally execute as the current Termux/Debian user.
- Use cybersecurity tools only against systems you own or are explicitly authorized to test.
- Never store API keys, access tokens, passwords, or local machine secrets in this repository.

## Next steps

1. Integrate a real ANSI/xterm-compatible terminal view and keyboard controls.
2. Improve session management and bridge lifecycle.
3. Add terminal resize and reconnection handling.
4. Validate interactive Codex/OpenCode sessions and all tool-group actions on-device.
5. Add Gradle wrapper, CI build, and reproducible debug APK instructions.

## License

No license has been selected yet. Until one is added, all rights are reserved by the copyright holder.
