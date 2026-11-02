# Desktop clipboard control candidate

Prepared against the attached files whose Git blob SHAs match the inspected dev sources:

- MeshCentralClient.kt: adba30e81bfed439513ad6371999fff4f961a7db
- DesktopRelayClient.kt: 9b4cac186fb126dc8a8f888e3e9b769715485aed
- RemoteDesktopScreen.kt: f313e71377e4b36781138d6853a328ca3847aff6
- RemoteDesktopViewModel.kt: 7f3494e9b90bc23c2e77f82b17c06088f73d0de0

Use a single directed control-channel action:msg/type:getclip or setclip request per user action. Remove outbound KVM command-9 probes and the repeated/undirected aliases. Retain inbound KVM command-9 content for agents that send it spontaneously, with declared-length validation. This candidate favors the agent control clipboard protocol, not clipboard-as-keystrokes.

Decode only explicitly recognized clipboard message types. Content requires a real string payload, including an empty string; write acknowledgement requires a boolean success field and never publishes clipboard content. On the shared control channel, reject messages without a node. Only a dedicated desktop channel may supply its own node as a fallback. Failed reads currently time out rather than exposing a dedicated agent-error string.

Bind replies once per MeshCentralClient through DesktopRelayClient. Route only to an existing, not-user-disconnected session owned by that exact mesh client; do not create a session from a reply. Dispatch updates asynchronously to avoid a control/session monitor lock inversion. Preserve pre-existing callbacks. Clipboard data remains process-memory state, not terminal history or diagnostics content.

Retain text exactly, without trim or NUL removal. Empty responses clear the remote clipboard field and can be copied deliberately to Android. Auto-clipboard applies received data only, not the empty placeholder used while waiting. The existing user-facing auto-copy toast remains unchanged.

Expose separate read/write states: waiting, received, remote-confirmed, failed, timed out. Show confirmation only after a recognized setclip success acknowledgement, not on local socket enqueue. Timeout is five seconds; an agent that does not acknowledge a successful write can show unconfirmed/timed-out. Do not resend automatically. Disable the corresponding button while waiting and reject another same-direction request in the session. Cancel outstanding operation timers when the desktop transport retires. No automatic retries or cross-session replay.

Limitations: no end-to-end request-ID correlation is claimed. A late response for the same node after a timeout can still be accepted; multiple clients or changes in remote clipboard ownership can race. Success means the agent confirmed the request, not that every remote application will paste it. Only plain text is covered. Device checks on Windows and Linux and CI compilation/JVM tests remain required.

Verification prepared: ClipboardControlProtocolTest covers exact whitespace, empty/missing responses, unrelated messages, malformed payloads, acknowledgements, node requirements and directed commands. Static checks verify unchanged keyboard, mouse, Unicode and non-clipboard transport methods plus XML validity. Kotlin/Android tests have not been run in the assistant environment. No APK has been built.

Device acceptance:
1. Send a short value and paste it in a remote text editor. Verify write status separately.
2. Copy a different remote value, request it, and copy the received value to Android.
3. Repeat with leading/trailing spaces, newlines, accents and an empty clipboard.
4. Open two nodes and check replies never populate the other node.
5. Disconnect during a request; ensure no repeated transfer or replay after reconnect.
6. Check failure/timeouts without claiming success; recheck keyboard punctuation and terminal IME behavior.

This package is a review candidate, not a published commit or a verified regression fix.
