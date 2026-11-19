# Device controls, batch actions and appearance

English | [Español](es/device-controls.md)

## Browsing and selection
Sorting, filtering, local favorites and selection operate within the active account and server context. Selection is reconciled with visible inventory; a changed authenticated context invalidates sensitive actions. The operating-system name preference preserves saved choices and does not control filter-icon highlighting. Power state is not inferred solely from connectivity.

## Reviewed batch actions
Select devices or visible groups, then review the action and eligible targets. JSON export is local. Remote actions include power requests, tags, notifications, commands, group moves, agent updates, clearing/loading the default core, uninstallation, device removal and upload. Availability depends on the action, server, permissions and agent. Core operations and uninstallation require a connected compatible agent and the conservative full-rights checks used by those actions.

Review and confirmation do not form an atomic server transaction. Context is rechecked before sending. One attempt is made per approved target, without automatic resend. Stopping pending work does not undo requests or stop remote commands already started.

Acceptance, local queuing, received output, limited file observations and verified changes are different outcomes. Receiving command output does not prove successful completion. Upload observations do not verify a remote content hash. Read every result label and uncertainty notice.

Dialogs keep the shared theme, adapt width and height to available space and content, and keep title/actions outside the scroll area. Long lists, small windows and the keyboard can still require scrolling. Group-move choices show names rather than internal identifiers.

## Remote desktop
Monitor selection depends on reported agent capabilities. The mouse-mode toggle is persistent; the separate floating right-click assistant is one-shot. Its toolbar control follows the collapse control. Scroll preserves the previous click mode, and Information is the final toolbar action. These controls do not imply that every planned desktop tool is implemented.

## Appearance
System follows Android; Light and Dark are fixed. Scheduled uses device-local start times for light and dark, which must differ. Automatic solar uses a manually selected reference city from a bundled catalogue of 24 cities and local sunrise/sunset calculations in that city zone. It does not request location access, use GPS or query a location service. Change the reference city when travelling. Times are approximate; absent city or unavailable events temporarily fall back to System while preserving the selected mode.

Appearance refreshes while the app is active and when resumed; it does not require a background alarm. Existing saved fixed-theme choices remain available. The compact selector has fixed icon/text columns and adapts to narrow screens.

## Validation limits
Build, JVM tests and lint are publication gates. Compiling instrumentation tests is not executing them. Check both languages, font scaling, portrait/landscape, keyboard insets and remote outcomes on the intended devices and server. Sharing and other planned tools must not be treated as delivered features.
