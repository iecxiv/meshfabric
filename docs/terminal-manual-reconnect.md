# Manual terminal reconnection — first increment

- Disconnecting the terminal screen closes only its transport. History and the retained renderer remain readable; the close button no longer leaves the screen.
- Reconnect creates an independent candidate with a total 30-second deadline, waiting for the existing control connection before opening a terminal tunnel. It does not retry the terminal automatically.
- Failed/cancelled candidates cannot replace the prior record. Handshake success promotes a new TerminalSession; archived renderers remain bound to their original session objects.
- The previous-history picker is read-only. Live and previous output never share a journal. Input is gated in Compose, the renderer, JavaScript, and the session transport.
- Records are kept in memory for this ViewModel lifetime. This increment does not add archive persistence, terminal serialization, or guarantees about remote process continuity. Existing current-buffer persistence settings are unchanged.
- Retained WebViews continue to use the existing four-renderer LRU. If evicted, replay is best effort from retained terminal output, not an exact restoration of alternate-screen applications.
- The handshake protocol, xterm version and resize algorithm are unchanged.

## Validation

CI must run all debug unit tests and assembleDebug before publishing the functional commit. Gate tests are not end-to-end socket or Android UI tests.

On a device: disconnect Wi-Fi; verify scroll/copy and blocked keyboard/paste/keys; fail and cancel reconnect; reconnect and issue clear; inspect previous history; navigate out/back; repeat reconnects; open nano and toggle keyboard/orientation to check resize regressions. Confirm that voluntary disconnect never starts automatic terminal reconnect. Confirm persistence enabled/disabled after app restart.

History replay correction: read() keeps its original strict gap detection, including a null startup cursor. Only an initial disconnected-view replay uses explicit readHistory(). Existing continuity tests are unchanged; additional tests cover explicit history replay and stale cursors.
