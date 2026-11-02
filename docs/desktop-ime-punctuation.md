# Desktop committed-text punctuation candidate

The existing DesktopSession.sendChar sends punctuation and non-ASCII characters through send10ByteUnicodeKey (command 1, length 10). MeshCentral's web client SendStringUnicode/SendKeyUnicode instead uses command 85 (KEYUNICODE), length 7, a down/up byte of 0/1 and a big-endian UTF-16 code unit. Reference: Ylianst/MeshCentral public/scripts/agent-desktop-0.0.2.js, SendStringUnicode and SendKeyUnicode.

Route RemoteDesktopViewModel.sendUnicodeText through the new pure DesktopTextEncoding encoder. Keep existing ASCII letter/digit/space/control virtual-key choices and balanced Shift for uppercase. Encode punctuation and other UTF-16 units as command-85 down/up pairs, preserving input order. Supplementary characters follow the web client's UTF-16-unit approach; this does not guarantee every remote application supports them.

Enqueue each committed text call in order directly to the captured connected desktop socket, under the session monitor used by socket retirement. Stop on enqueue failure, with no replay onto another session. This replaces the old delayed per-call coroutine on this UI text path; it does not guarantee delivery acknowledgements, recovery of partial strokes or ordering with all other keyboard senders.

Scope: committed desktop UI text only. Physical key routing, shortcut handling, terminal code, clipboard operations and the legacy DesktopSession.sendUnicodeText/sendUnicodeKey methods for other callers remain unchanged. This is protocol encoding, not a Windows-only UI workaround. No text or key-content logging is added.

This does not resolve the separate hypotheses about an IME delivering native KeyEvents or replacing equal-length text in the hidden field. If punctuation still fails, inspect those paths rather than treating the packet fix as complete device validation.

Added JVM tests cover exact period/comma bytes, other punctuation, accents, UTF-16 surrogate units, packet ordering, unchanged ASCII/control encoding, balanced uppercase Shift and empty input. These tests have not been compiled or executed in the assistant environment (Kotlin compiler unavailable); APK build and device validation remain pending.

Acceptance: in a remote plain-text editor type a.b,c;d:e-f_g@h/i\j, then test accents, rapid mixed text, uppercase, deletion, Enter, Tab and physical shortcuts. Confirm no omissions or duplicates. Test Windows and Linux separately; do not execute the test string as a shell command. Check disconnect/reconnect without replay. The native-key path and composition/replacement behavior remain separate test cases.
