# Touch scroll routing only

English | [Español](es/terminal-touch-scroll.md)

On coarse primary pointers, the rendered screen passes hit testing to the existing native xterm viewport only in the normal buffer with public mouseTrackingMode equal to none. The class is updated after opening and after each parsed output batch, and removed on failure/disposal. Missing mode APIs fail closed. Fine-pointer devices and alternate/mouse-reporting modes retain existing hit testing. No synthetic wheel events, custom scrolling physics, sensitivity changes, input forwarding, focus/IME changes, resize or transport changes.

Tradeoff: direct canvas selection and canvas link taps are not preserved while this mobile pass-through is active. Use the tested Select and copy menu for text selection. The native copy/paste dialogs are outside the CSS selector and unchanged.

CI reuses the actual bundled xterm and the same stylesheet as production. An Android test injects touchscreen swipes starting on text and blank columns, requiring both to move at least three rows; checks hit testing reaches the viewport; checks restoring mouse and alternate-buffer routing. Existing echo/copy/paste, Node and JVM tests also run. Device confirmation remains necessary for perceived smoothness, inertia, taps/keyboard and scroll position during incoming output.
