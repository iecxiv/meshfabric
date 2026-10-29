# Terminal refactor — phase 2: native wire protocol

## Scope

Build on phase 1 without changing the public TerminalSession/TerminalRelayClient API or WebView code. Keep existing authentication, socket generation fencing and disconnected-buffer policy.

- Replace three speculative resize variants with one `{"ctrlChannel":"102938","type":"termsize","cols":...,"rows":...}` packet, matching upstream MeshCentral `views/xterm.handlebars`.
- Send an `options` control message with the latest desired grid before protocol selector `1` starts the PTY, matching upstream agent redirect / meshctrl initialization.
- Deduplicate only packets accepted by the local WebSocket queue. This is not a remote acknowledgement.
- Reset deduplication and incremental UTF-8 decoder on socket retirement. Never carry partial bytes between socket generations.
- Delayed post-handshake resize reads the current desired dimensions, not dimensions captured before a keyboard/layout change; cancel obsolete scheduled jobs.
- Interpret `c`/`cr` as handshake only before connection. A later `1`, `c` or `cr` is ordinary terminal text. Binary output is never parsed as relay control.
- Incrementally decode UTF-8 binary output, preserving multibyte characters split across frames.
- Handle channel-qualified ping/pong and console control messages separately from terminal output.
- Retire the transport if a user-input or resize send is rejected by the local socket queue.
- Bound retained in-memory history after large writes, while retaining the existing disk buffer format.

## Tests

12 JUnit cases in `TerminalProtocolTest.kt` cover message syntax, handshake classification, UTF-8 splits/reset/malformed data, deduplication, rejection/retry, new socket generations, forced sends and invalid geometry.

    ./gradlew :app:testDebugUnitTest --tests iecxiv.meshfabric.data.remote.TerminalProtocolTest

No Android build, JUnit execution or remote agent interoperability is claimed unless separately reported. The upstream source checked is not necessarily the user's deployed server/agent version.

## Still pending (phase 3)

TerminalScreen's readiness fallback, WebView lifetime, replay of raw history, atomic history/live-output handoff and lossless native-to-WebView delivery remain unchanged. `tryEmit` rejection is now logged, not recovered. Phase 2 does not eliminate upstream output loss or preserve an alternate screen across WebView recreation.

## Device checks

Open nano; show/hide keyboard; rotate twice; change font size. Compare `stty size` before/after. Verify accented text and emoji during rapid output. Reopen the view without mistaking a new shell for a resumed PTY. Verify shell and supported Windows terminal modes against the deployed MeshCentral agent. No blanket guarantee of nano resize correctness is made before these checks.
