# Typed terminal receive path and real WebView echo checks

Binary WebSocket output is delivered directly to handleTerminalOutput and never interpreted as c/cr/1 handshake. Text-frame handshake compatibility is retained before connection; connected text uses the same output publisher. No change to resize packets, renderer retention, input encoding or local echo.

Four Android instrumentation cases drive the production TerminalSession receive handlers, the journal and parser ACK delivery, then inspect the real bundled xterm buffer inside an Android WebView. They cover ac1b and cc11 as isolated binary/text characters. The socket is fake and no remote commands or credentials are used. The test page wraps the bundled Terminal constructor only in its synthetic HTML; no inspection hook is added to production assets. These cases do not reproduce a full MeshCentral network connection or Android keyboard input.

Debug builds log at most 64 frame records per session and corresponding output lengths/revisions under TerminalEcho. Logs contain frame kind, lengths and connection flags, never terminal text or keystrokes. If the real-device symptom persists, these metadata traces are needed to locate the remaining loss; this change alone does not establish the previous runtime cause.

The helper Activity and its unexported manifest entry exist only in the debug build. CI runs existing JavaScript/unit tests, assembles app and instrumentation APKs, and runs the four WebView cases on an API-35 emulator before publishing. Physical-device validation remains required.
