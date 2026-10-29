# Terminal refactor — phase 1

## Scope

Keep the bundled xterm.js and FitAddon versions and the existing Android bridge API.
Replace inline JavaScript with a runtime and an independently testable resize coordinator.
Do not change desktop, files, authentication or socket routing.

- One 120 ms trailing-edge resize coordinator for container, orientation, visual viewport and font changes.
- Measure through FitAddon.proposeDimensions(); no direct xterm private-field access or guessed character metrics in application code.
- Invalid or hidden geometry never replaces the last valid grid.
- Wait for the active parser write before applying a resize.
- Serialize writes with xterm's completion callback; preserve the alternate buffer and a user's scrollback position.
- Restore native xterm viewport CSS rather than forcing scrollTop or disabling its viewport.
- Preserve terminal line endings (convertEol=false).
- Pending JavaScript output is limited to 2 Mi UTF-16 code units, including the in-flight write. Overflow displays an explicit error instead of silently corrupting the stream. This is not end-to-end backpressure.
- Initialization errors do not send the JavaScript ready signal.

## Automated tests

Run from repository root with Node.js:

    node app/src/test/js/terminal-runtime.test.js

These tests use fake terminal/bridge objects. They do not validate actual xterm rendering, WebView layout, Android IME or a remote PTY.

## Known remaining work

This commit is not the full terminal refactor. TerminalScreen still has the onPageFinished readiness fallback and raw history replay. Kotlin's SharedFlow tryEmit path, packet-level UTF-8 decoding, WebView/session ownership and the three remote resize message variants are unchanged. Investigate the MeshCentral agent's supported terminal-size protocol before replacing those variants. Do not treat JavaScript ordering as a guarantee against upstream data loss.

## Device acceptance matrix (not yet executed)

Test shell and nano with keyboard show/hide, portrait/landscape round trips, font changes, navigation away/back, fast output, selection/paste and scrollback reading. Verify remote `stty size` against the displayed grid. A static screen must not cause a reconnect. Inspect nano's status bar and cursor after every resize. Cover multiple sessions and network loss without assuming an old PTY survives a new tunnel.
