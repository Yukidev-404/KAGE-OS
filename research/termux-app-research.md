# Termux App — Research Report

Source analyzed: `D:\Terminal dial\research\termux-app` (Termux `v0.118.0`, single commit `8629e63`, master branch).

## 1. What Termux is

Termux is an Android terminal application plus a Linux environment. The repository contains only the app/UI and terminal emulation; installable packages live in `termux/termux-packages`. The app:

- Runs as an Android app under package `com.termux` with a shared Linux UID shared with its plugins (Termux:API, Boot, Float, Styling, Tasker, Widget).
- Provides a foreground `TermuxService` that keeps terminal sessions and background tasks alive.
- Embeds a minimal bootstrap (APT-based) into the APK and extracts it into `$PREFIX` (`/data/data/com.termux/files/usr`) on first run.
- Exposes a terminal emulator (VT/xterm) implemented in Java + a small JNI/pty layer, plus an Android `View` renderer.
- Exposes integration surfaces for external apps: `RUN_COMMAND` intent, a `ContentProvider` (`com.termux.files`), Storage Access Framework provider, and a Unix socket for `termux-am`.

## 2. Module / build layout

`settings.gradle`: `:app`, `:termux-shared`, `:terminal-emulator`, `:terminal-view`.

| Module | Type | Purpose |
|---|---|---|
| `app` | Android application | UI, services, installer, file receiver, settings |
| `terminal-emulator` | Android library | PTY/JNI, VT state machine, screen buffer, key handling |
| `terminal-view` | Android library | `TerminalView` Android View + `TerminalRenderer` |
| `termux-shared` | Android library | Constants, utils, shell manager, preferences, logging, termux-am socket |

The libraries are published to JitPack/Maven (`com.termux:terminal-emulator:0.118.0`, etc.) and can be consumed by other Android apps.

## 3. Application lifecycle

`TermuxApplication.onCreate()` (`app/.../TermuxApplication.java`):
- Sets crash handler (`TermuxCrashUtils`).
- Reads log level from shared preferences.
- Sets bootstrap package manager/variant from `BuildConfig.TERMUX_PACKAGE_VARIANT`.
- Initializes `TermuxAppSharedProperties` (from `~/.termux/termux.properties`).
- Initializes `TermuxShellManager`.
- Sets night mode.
- Checks Termux files directory accessibility; if OK, creates `apps/termux-app`, sets up the `termux-am` socket server, initializes `TermuxShellEnvironment` and writes `termux.env`.

`TermuxActivity` (main UI, singleTask, exported) binds to `TermuxService`, hosts `TerminalView`, a drawer session list, extra keys, and the terminal toolbar. `TermuxService` is a foreground service holding `TermuxSession`s (foreground terminals) and `AppShell`s (background tasks), wake/wifi locks, and notifications.

## 4. Terminal core

### 4.1 Native (JNI) pty layer
`terminal-emulator/src/main/jni/termux.c` + `JNI.java`:
- `createSubprocess(cmd, cwd, args, envVars, processId[], rows, cols, cellWidth, cellHeight)` opens `/dev/ptmx`, `grantpt`/`unlockpt`/`ptsname_r`, configures termios (IUTF8, disables IXON/IXOFF), sets winsize via `TIOCSWINSZ`, `fork()`s. Child becomes session leader, opens pts slave, dups to 0/1/2, `chdir`, `execvp`.
- `setPtyWindowSize`, `waitFor`, `close`.

### 4.2 Java core classes
- `TerminalSession` — process + pty orchestration; spawns reader/writer/waiter threads; owns two `ByteQueue`s; implements `TerminalOutput`; triggers `TerminalEmulator`.
- `TerminalEmulator` — VT/xterm state machine: cursor, margins, DECSET flags, colors, CSI/OSC/DCS/APC parsing, mouse, resize; emits into `TerminalBuffer`.
- `TerminalBuffer` — circular buffer of `TerminalRow` (screen + scrollback); external/internal row mapping; resize/reflow; scroll; text selection; sixel/iTerm bitmap storage + GC.
- `TerminalRow` — fixed-width row: `char[] mText`, `long[] mStyle`, wrap flag; combining/wide char handling.
- `KeyHandler` — maps Android keycodes + modifiers to terminal escape sequences (termcap lookup).
- `TextStyle`, `TerminalColors`/`TerminalColorScheme`, `TerminalBitmap`/`TerminalSixel`/`ITermImage`.

### 4.3 Rendering
`terminal-view`:
- `TerminalView` (View) holds `mTermSession`, `mEmulator`, `mRenderer`, scroll top row. `onDraw` → `mRenderer.render(...)`.
- `TerminalRenderer.render(...)` loops visible rows, groups runs by style/cursor/selection/font width, draws backgrounds, cursor shapes, text (with bold/italic/underline/strikethrough/reverse/dim), wide & combining chars, inline bitmaps (sixel).
- `TerminalView.updateSize()` computes columns/rows from dimensions and font metrics, calls `mTermSession.updateSize`, restores top row.
- Touch/gestures: scrolling, fling, scale (zoom), text selection (`TextSelectionCursorController`), mouse events.

### 4.4 Data flow
PTY bytes → `TerminalSession` reader thread → `mProcessToTerminalIOQueue` → main thread `MainThreadHandler` → `mEmulator.append()` → `TerminalBuffer` rows → `TerminalView.invalidate()` → `TerminalRenderer.render()`.
User input → `TerminalView` → `TerminalSession.write`/`writeCodePoint` → output writer thread → pty.

### 4.5 Coupling note
`TerminalSession` is `final` and tightly coupled to spawning a local process via `JNI.createSubprocess`. `TerminalView.attachSession(TerminalSession)` requires a `TerminalSession`. There is no built-in "remote/socket-backed" session. To drive the emulator from a socket (as Terminal Dial's bridge does), options are:
1. Fork `terminal-emulator`/`terminal-view` and add a socket-backed `TerminalSession` (or a generic `TerminalOutput`-backed session).
2. Use `TerminalEmulator` directly with a custom `TerminalOutput` implementation that writes to the socket, and render with `TerminalRenderer` in a custom view (reimplementing `TerminalView`'s glue).
3. Keep the bridge protocol but feed output bytes into a `TerminalEmulator` and send input bytes from key events.

## 5. Bootstrap & packages

`TermuxInstaller.setupBootstrapIfNeeded()` (`app/.../TermuxInstaller.java`):
1. Check primary user and files-dir accessibility; error dialogs/notifications if not.
2. If `$PREFIX` exists and non-empty → done; if empty → reinstall.
3. Show progress dialog; background thread:
   - Delete staging + prefix dirs.
   - Create staging + prefix dirs with permissions.
   - `loadZipBytes()`: load native `libtermux-bootstrap`, call `getZip()` to get embedded (arch-specific) bootstrap zip bytes.
   - Extract zip into staging: parse `SYMLINKS.txt` (arrow `←`) to create symlinks; extract other entries; chmod 0700 for `bin/`, `libexec`, apt helpers.
   - `Os.symlink` for links.
   - Rename staging → prefix.
   - Rewrite env file (`TermuxShellEnvironment.writeEnvironmentToFile`).
4. On failure: bootstrap error dialog with retry/abort.
5. `setupStorageSymlinks()`: clears `~/storage`, then symlinks shared/documents/downloads/dcim/pictures/music/movies/podcasts/audiobooks (Q+), plus `external-i` and `media-i` into `~/storage`.

Native: `termux-bootstrap.c` (JNI `getZip` returning embedded blob) + `termux-bootstrap-zip.S` (`.incbin` of `bootstrap-{i686,x86_64,aarch64,arm}.zip`).

Bootstrap variant is chosen at build time via `TERMUX_PACKAGE_VARIANT` (`apt-android-7` default, or `apt-android-5`).

## 6. Security model

### 6.1 Identity & permissions
- `sharedUserId="${TERMUX_PACKAGE_NAME}"` (`com.termux`) so Termux + plugins share a Linux UID.
- Custom permission `com.termux.permission.RUN_COMMAND` (`protectionLevel="dangerous"`).
- Exported components: `TermuxActivity`, `HomeActivity`, `SettingsActivity`, `FileShareReceiverActivity`/`FileViewReceiverActivity`, `RunCommandService` (guarded by RUN_COMMAND), `TermuxDocumentsProvider` (guarded by MANAGE_DOCUMENTS), `TermuxOpenReceiver$ContentProvider` (guarded by RUN_COMMAND).
- `TermuxService`, `TermuxOpenReceiver`, `SystemEventReceiver` are not exported.

### 6.2 allow-external-apps policy
`~/.termux/termux.properties` key `allow-external-apps` (default `false`) (`TermuxSharedProperties.shouldAllowExternalApps()`). Checked by `TermuxPluginUtils.checkIfAllowExternalAppsPolicyIsViolated()`. Applied in `RunCommandService` and in `ContentProvider.openFile`. Violations trigger forced notifications.

### 6.3 File sharing & providers
- `FileReceiverActivity` handles SEND/VIEW, saves streams into `$TERMUX_FILES_DIR/home/downloads`, then invokes user scripts `termux-file-editor` / `termux-url-opener`. Note: `saveStreamWithName` uses `new File(receiveDir, attachmentFileName)` without sanitizing `attachmentFileName` (potential `../` traversal).
- `TermuxDocumentsProvider` exposes `$HOME` via SAF; `getFileForDocId` uses raw docId path with limited containment checks in search only.
- `ContentProvider` (`com.termux.files`) canonicalizes path and restricts to Termux files dir or external storage; forces `termux.properties` / `termux.float.properties` to read-only to prevent silent security-property changes.

### 6.4 Other
- Phantom processes (Android 12+): `PhantomProcessUtils`, `FeatureFlagUtils`; requires `DUMP` + `PACKAGE_USAGE_STATS`.
- SELinux: `SELinuxUtils` reads process/file contexts via reflection; exported to shell env; am socket server only allows same-UID or root peers.
- `termux-am` socket server (`TermuxAmSocketServer`) is started by `TermuxApplication` if `KEY_RUN_TERMUX_AM_SOCKET_SERVER` is true; socket file in private app data dir; state frozen after start.

### 6.5 Attack surface (summary)
1. Exported `RunCommandService` reachable by any app holding RUN_COMMAND permission when `allow-external-apps=true` → command execution in Termux UID.
2. Exported `ContentProvider` allows file read/write (except protected props) to RUN_COMMAND holders.
3. SAF document provider exposes home dir to MANAGE_DOCUMENTS holders; path handling not fully canonicalized.
4. File receiver path traversal via unsanitized attachment filename.
5. File receiver can be induced to read arbitrary `file://` paths from VIEW intents.
6. Shared UID means plugin compromise affects Termux data.
7. High-privilege permissions (storage, logs, install, usage stats) amplify impact.

## 7. Integration guidance for Terminal Dial

Terminal Dial currently renders output as plain text and wants a real ANSI/xterm view. Termux provides reusable pieces:

- **Dependencies**: add JitPack and depend on `com.termux:terminal-emulator:0.118.0` and `com.termux:terminal-view:0.118.0` (terminal-view depends on terminal-emulator). `termux-shared` is heavier and not needed for pure terminal rendering.
- **View**: host `com.termux.view.TerminalView` in Compose via `AndroidView`, call `setTerminalViewClient(...)`, and `attachSession(session)`.
- **Client**: implement `TerminalViewClient` (scale, single-tap, back-button mapping, ctrl/alt/shift/fn reads, key callbacks, logging).
- **Session problem**: `TerminalSession` spawns a local process. Terminal Dial's shell lives in a Python PTY bridge on `127.0.0.1:8765`. Options:
  - Fork the (small) `TerminalSession`/`TerminalEmulator` wiring to read/write over a socket: feed incoming bridge `output` packets into `TerminalEmulator.append()`, and send `input` packets from `TerminalOutput.write()` / key handling.
  - Or replace the bridge with a direct PTY by running the Debian command inside Termux via `RunCommandService`/`termux-am` — but that loses the persistent-shell-across-reconnect property unless carefully managed.
- **Resize**: bridge already supports `resize` packets with rows/cols; `TerminalView.updateSize()` computes dimensions and calls `session.updateSize(...)`; forward to bridge as `resize`.
- **Input**: `TerminalView` calls `session.write(...)` for keystrokes; route those to bridge `input` packets.
- **Lifecycle**: keep a single socket connection; on disconnect, buffer in the bridge (it already keeps a backlog) and replay on reconnect. Termux's `TerminalView` can be detached/reattached to a new session without losing the emulator if you keep the `TerminalEmulator`/`TerminalSession` alive.
- **Security**: bridge binds to `127.0.0.1` only (already done). Do not expose. Termux's `allow-external-apps` policy is a useful model if Terminal Dial ever accepts external commands.

## 8. Key files reference

- `app/src/main/AndroidManifest.xml` — components, permissions, sharedUserId.
- `app/src/main/java/com/termux/app/TermuxApplication.java` — init.
- `app/src/main/java/com/termux/app/TermuxService.java` — foreground service, sessions/tasks, wake locks, notifications.
- `app/src/main/java/com/termux/app/RunCommandService.java` — external RUN_COMMAND gateway.
- `app/src/main/java/com/termux/app/TermuxInstaller.java` — bootstrap extraction + storage symlinks.
- `app/src/main/java/com/termux/app/TermuxOpenReceiver.java` — file content provider.
- `app/src/main/java/com/termux/filepicker/TermuxDocumentsProvider.java` — SAF provider.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java` — process/pty orchestration.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java` — VT state machine.
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java` — screen/scrollback buffer.
- `terminal-emulator/src/main/jni/termux.c` — pty creation.
- `terminal-view/src/main/java/com/termux/view/TerminalView.java` — view + input.
- `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java` — rendering.
- `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` — paths/constants.
- `termux-shared/src/main/java/com/termux/shared/termux/shell/am/TermuxAmSocketServer.java` — am socket.

## 9. Open questions / not covered

- Exact `TerminalSessionClient` interface methods (title, bell, clipboard, colors, session finished) — needed to implement a remote session; inspect `TerminalSessionClient.java`.
- Whether Termux libraries are consumable from Kotlin/Compose without AndroidX version conflicts (termux-shared pulls appcompat, material, markwon, hiddenapibypass).
- Performance of `TerminalRenderer` on the target device; font metrics and DPI handling.
- How to handle Termux's extra-keys toolbar and soft-keyboard in a Compose UI.
