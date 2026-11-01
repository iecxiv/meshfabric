# Foreground-only terminal ACK watchdog

English | [Español](es/terminal-ack-recovery.md)

A pending parser acknowledgement has a 20-second deadline only while its renderer is attached and the current navigation lifecycle is RESUMED. Detach/background cancels the scheduled check without abandoning the pending output. Returning to RESUMED gives a fresh full deadline. Repeated RESUMED callbacks do not extend it. An idle/static terminal has no deadline.

On expiration the view shows ACK_TIMEOUT and stops advancing output. Late ACKs cannot silently clear that error. The socket, remote PTY and journal are not restarted. No Ctrl+L or other synthetic input is sent.

ACK_TIMEOUT and renderer loss offer an explicit, confirmed Recreate view action. It removes only the exact expected cached renderer, then constructs another renderer bound to the existing session. Starting the network session is keyed to node/navigation, not renderer replacement. OUTPUT_CONTINUITY gaps do not offer this button because replay cannot recover an already-truncated journal.

Recreation is not a VT snapshot restore. The confirmation warns that nano and other full-screen programs may need a redraw. Existing replay/geometry limitations remain. No automatic retry loop or session-disconnect fallback is added. A watchdog is a conservative timeout heuristic, not proof of a renderer-process crash.

Visibility bindings use tokens so a previous screen cannot pause a newer host. Recovery uses identity-checked removal so an obsolete button cannot destroy a replacement renderer. Observers are removed on disposal; Lifecycle/Activity objects are not stored by the renderer.

Fourteen new pure JVM watchdog tests and two store recovery tests are supplied. Existing JavaScript, pipeline, readiness, handshake and four real-WebView echo tests must pass in CI before publication. These do not yet exercise the recovery dialog or Android foreground/background transitions on a physical device. Check those manually, together with nano/keyboard/rotation and c/1 input.
