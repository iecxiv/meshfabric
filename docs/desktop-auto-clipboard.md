# Bidirectional automatic desktop clipboard

The existing autoClipboard switch now controls text synchronization for the visible desktop without opening the clipboard dialog. Manual clipboard transfer APIs and their status display remain unchanged.

Policy:
- On first activation, observe the current Android text and obtain a fresh remote snapshot as baselines. Do not overwrite either existing clipboard merely by enabling the switch. Synchronize subsequent changes.
- Android to remote uses the Android clipboard change listener. Remote to Android uses the directed control query every two seconds when no transfer is pending. Failed/timed-out reads use a ten-second interval.
- Operate only while the desktop is connected, the screen lifecycle is RESUMED and its window has focus. Remove listeners and cancel the coroutine when disabled, disconnected, stopped or disposed. Resume/focus regain rechecks local text before accepting a fresh remote snapshot.
- Keep policy state per visible node. On concurrent changes after focus regain, newly observed Android text takes priority; it is not overwritten by an older remote snapshot.
- Exact-content tracking suppresses echoes in both directions. The first remote snapshot after a local write is treated as an echo/conflict baseline. Failed writes are not retried automatically without another local change.
- Explicit empty text is a change. An absent primary clip is not treated as an instruction to clear the remote clipboard. URI/image or multi-item content is not coerced/read as text or forwarded; remote application is paused while unsupported local content is present.
- Only text is covered. No clipboard contents are logged or persisted. No application Toast is added; Android itself can display clipboard privacy notifications.

Limits:
- This is foreground synchronization, not background clipboard monitoring. Android clipboard access and device policies still apply.
- Cross-host clocks and end-to-end request IDs are not available. This is a deterministic local conflict policy, not a guarantee of globally ordered simultaneous edits.
- Linux agent clipboard failures reproduced in the web client are not fixed by this feature.
- If an agent does not acknowledge writes, the existing transfer timeout still applies. Device policy may reject an Android clipboard write.

JVM tests in DesktopClipboardAutoPolicyTest cover baseline activation, local/remote changes, echo suppression, queued conflicts, empty text, failed writes, reset, absent/unsupported content and independent node policies. Android focus/listener lifecycle and two-device integration still require device validation.

Acceptance: enable automatic clipboard and close Settings without opening the transfer dialog. Copy new text on Android, return to the focused desktop and paste remotely. Copy another text remotely and paste on Android. Repeat with empty text, rapid changes, disable/enable, two nodes, focus changes, disconnect/reconnect and background/resume. Verify there are no loops, no old-value overwrites on initial activation, and no reads/transfers while unfocused or stopped. Recheck manual transfer, keyboard punctuation and terminal keyboard behavior.
