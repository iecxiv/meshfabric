# Terminal output delivery after 32a5aaf

## Scope

No changes to resize scheduling, PTY size messages, handshake, input encoding, keyboard shortcuts or WebView retention. This is a delivery step on top of the validated phase 1 and readiness gate.

A per-session journal holds at most 1,048,576 UTF-16 code units (roughly 2 MiB of character storage plus runtime overhead). Each read has a stream epoch and absolute cursor. StateFlow revisions only wake the reader; conflating notifications cannot itself skip stored output. Legacy SharedFlow remains for API compatibility, but the terminal view no longer consumes it.

A WebView sends at most one 16,384-code-unit batch until its token is acknowledged by the callback of xterm.write. evaluateJavascript completion is not treated as parser completion. Old-view, wrong and duplicate acknowledgements do not advance the cursor. UTF-16 surrogate pairs are not split by journal reads.

If the bounded journal no longer contains a required range, or history has been cleared, delivery stops visibly. It does not silently fast-forward or disconnect/restart a remote shell. The error asks the user to disconnect and create a new session if continuity cannot be restored. This is bounded delivery with gap detection, not unlimited or network-level backpressure.

## Still pending

- Retaining the emulator/WebView across navigation and configuration changes.
- Accurate VT screen snapshots: disk history remains the existing raw ANSI format and may already be truncated. This change does not make replay at a different geometry correct.
- Renderer-process recovery and a foreground-aware ACK timeout. A missing ACK currently leaves one batch pending; the journal stays bounded and a future resume may detect a gap.
- Incremental UTF-8 transport decoding and protocol changes remain deliberately excluded after the previous regression.

## Verification

14 new Kotlin/JUnit pipeline cases, six existing readiness cases, and 21 JavaScript cases (14 existing plus seven new). GitHub Actions must run Node tests, assembleDebug and testDebugUnitTest successfully before publishing the functional commit. This does not replace physical-device tests.

Device checks: history followed by continuous output while opening, rapid away/back navigation, multiple sessions, fast output, nano keyboard open/close, rotation, font size, scrollback and shortcuts. Do not claim lossless delivery after process death or a remote PTY reconnect.
