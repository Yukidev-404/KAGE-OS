# Termux-side bridge

This service opens a pseudo-terminal running your existing Debian PRoot login and accepts a JSON-lines connection from the Android app. It binds only to `127.0.0.1:8765`.

## Requirements

- Termux installed and running on the same Android device as Terminal Dial.
- `proot-distro` with a Debian distribution named `debian`.
- Python 3 in Termux.

## Run

From the **Termux shell** (not from inside Debian):

```sh
pkg install python
python ~/terminal_dial_bridge.py
```

Copy `terminal_dial_bridge.py` to the path used above first. Keep Termux alive while you use the app. Android battery/background restrictions may stop a long-running service; service lifecycle handling will be improved in a later iteration.

The app connects to `127.0.0.1:8765`. Do not change the bind address to `0.0.0.0` or forward this port to a network.

## Protocol

Each frame is a single UTF-8 JSON object followed by a newline.

- App to bridge: `{"type":"input","data":"pwd\\n"}`
- App to bridge: `{"type":"action","name":"SYSTEM"}`
- Bridge to app: `{"type":"output","data":"..."}`
- Bridge to app: `{"type":"status","message":"..."}`

The action names are fixed allowlisted tool groups. The terminal itself is a normal shell, so commands typed by the user execute with the permissions of the Termux/Debian environment.

## Current limitations

- The first version uses a plain text output pane, not a full ANSI/xterm emulator; interactive applications that rely on cursor addressing, alternate screens, or function-key sequences will not render correctly yet.
- Session lifetime is tied to this bridge process. The PTY can survive an app disconnect/reconnect while the bridge remains running, but stopping the bridge ends the shell.
- A later version should add proper terminal emulation, terminal resize propagation, and managed background lifecycle.
