# Third-Party Notices

KAGE//OS includes source code copied or adapted from the following projects.

## Termux terminal-emulator and terminal-view

Source: https://github.com/termux/termux-app
Modules: `terminal-emulator/`, `terminal-view/`
License: Apache License 2.0 (originally derived from Android Terminal Emulator)

Copyright (c) Termux and contributors.

The copied modules are used under the Apache License, Version 2.0.
See the upstream repository for the full license text and notices.

Modifications in this project:
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java` was extended with a remote (socket-backed) session mode that avoids local process creation.
- Build files were adjusted for this project's SDK versions.

No GPL-licensed Termux application code or `termux-shared` module code is included.