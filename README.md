# Terminal Dial

**Touch-controlled Android terminal with a rotary cybersecurity tool selector.**

Terminal Dial is a native Android app concept for controlling an existing Termux/Debian environment through a local bridge. It is not intended to fake a shell or bundle a second, disconnected Linux environment.

## Planned experience

- Portrait: terminal at the top, rotary selector below.
- Landscape: terminal on the left, selector on the right.
- Touch/drag to rotate through tool groups; tap or activate the center to select.
- Terminal output and input remain available while a selected task runs.
- Initial groups: RECON, WEB, REVERSE, CODEX, OPENCODE, SYSTEM.
- Preserve work across orientation changes and reconnects where the bridge/session manager supports it.

## Architecture

- **Android client:** Kotlin, Jetpack Compose, custom rotary selector.
- **Execution environment:** the user's existing Termux + Debian PRoot installation.
- **Local bridge:** a small, explicitly started Termux-side service. It must bind to loopback only, validate messages, and expose only documented actions by default.
- **Long-running sessions:** use a persistent session manager such as `tmux` on the Termux/Debian side rather than tying task lifetime to an Activity.

Android apps are sandboxed. The Android client cannot directly execute binaries in Termux's private directories; the bridge is an intentional integration point. The bridge is not a security boundary against other processes running under the same Android/Termux user, so it should not be exposed to a LAN or the internet.

## Safety defaults

- Bind the bridge to `127.0.0.1` only.
- Do not run as root or claim PRoot is a security boundary.
- Prefer fixed, documented tool actions over silently running arbitrary shell commands.
- Use cybersecurity tools only against systems you own or are explicitly authorized to test.
- Never store API keys, access tokens, passwords, or local machine secrets in this repository.

## Status

Early development. The repository currently documents the product and architecture; a working terminal connection, ANSI terminal rendering, tool actions, persistence, and APK packaging still need implementation and testing on the target Android device.

## Development plan

1. Build the Compose layout and rotary selector.
2. Define and implement a loopback-only bridge protocol.
3. Add a persistent PTY/session backend in Termux/Debian.
4. Add allowlisted tool launchers and connection diagnostics.
5. Test portrait/landscape, reconnects, interactive CLIs, and low-storage behavior.
6. Add reproducible debug APK build instructions.

## License

No license has been selected yet. Until one is added, all rights are reserved by the copyright holder.