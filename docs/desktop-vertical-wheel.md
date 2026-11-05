# Physical vertical wheel in remote desktop

Capture Compose Scroll events from Mouse pointers over the remote image and forward vertical wheel units using the existing sendMouseScroll transport. No touch Scroll mode selection is required. Horizontal-only events are ignored; for mixed events, only the vertical component is forwarded.

Use a separate pointerInput listener in the Initial pass. Ignore previously consumed changes. Consume accepted vertical events once, including sub-unit events retained in the fractional accumulator. The existing touch gesture handler is not rewritten. Keep the latest callback without restarting the accumulator on every frame recomposition.

Gate forwarding with the existing desktopKeyboardActive value (connected, RESUMED, no keyboard/clipboard/settings dialog) plus native window focus. Attach to the remote image interaction Box, not the screen root or dialogs. Image hit testing rejects letterbox margins and points outside the viewport. Existing overlay hit testing still requires device verification.

Project pointer coordinates through the same centered fit, zoom and pan transform used for the image. Convert normalized coordinates with the current remote resolution and clamp to valid pixels 0..size-1. Respect reverseMouseWheel. Compose Y positive-down maps to negative KVM units, with 120 units per wheel step. Retain fractional units and bound each output to the signed 16-bit transport range. Reset fractional state when disabled, geometry/inversion changes, focus is absent during an event, hit testing fails, or forwarding is rejected.

Scope: vertical physical mouse wheel only. No horizontal-wheel protocol, wheel zoom, middle-click changes, clipboard changes, terminal changes or version bump. Some trackpads can be reported as Mouse by the platform; mouse-like vertical Scroll events may therefore follow this path.

Prepared JVM tests cover projection, letterbox rejection, zoom/pan, invalid inputs, pixel bounds, direction/inversion, fractions and output limits. They do not prove Android event routing. Build and JVM tests must pass before publication; physical-device validation remains required.

Acceptance: test a USB/Bluetooth mouse and Samsung DeX if available, both directions and inversion; no touch Scroll mode requirement. Check zoom/pan positioning, image margins, local toolbar and all dialogs, fullscreen, rotation, disconnect/background and fractional/high-resolution wheels. Verify touch drag/pinch/scroll and remote keyboard remain unchanged. Confirm there is one remote displacement per event and local dialogs scroll without forwarding to the remote.
