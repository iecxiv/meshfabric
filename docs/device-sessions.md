# Device sessions and chat

English | [Español](es/device-sessions.md)

## Device cards

Use the card chevron for General, Events, Details and the agent Console. Availability depends on the authenticated account, current permissions, inventory and remote capabilities. The redundant information icon beside the connection badge is removed; Details remains in the expanded options. Sharing is hidden until its implementation is complete.

## Disconnect all confirmation

The Devices header action asks for confirmation before closing active sessions by default. Select Do not ask again and confirm to skip that dialog on subsequent uses. Cancelling or dismissing the dialog does not change the saved preference or request disconnection. With confirmation disabled, tapping the header action closes active sessions directly.

Settings provides Confirm disconnect all. Turn it on to restore the confirmation. The preference is stored locally in app-private SharedPreferences and is not synchronized with the MeshCentral server. It remains saved across normal app restarts; clearing app data resets it.

This changes the confirmation of the existing closeAllActiveSessions action, not its session scope. It is not a logout action and does not add automatic reconnection.

## Remote desktop

The connection subtitle shows Connected for one viewer or an unknown count, and Connected, 2 viewing or Connected, 3 viewing for multiple viewers. The total sums the connection counts in server metadata, not just distinct accounts. Disconnecting clears the previous count. Metadata updates do not request a screen refresh.

Desktop resume and refresh are distinct protocol operations. Compression settings carry the frame interval, and changing frame rate updates those settings. There is no periodic refresh loop acting as a heartbeat. Toolbar movement, collapse/expand, physical mouse dragging and viewport retention require validation on the intended Android and remote devices.

The floating toolbar information button opens gesture help in English or Spanish. Its visible icon is 18 dp with gray #9E9E9E; its existing 32 dp IconButton layout is unchanged. See [Desktop touch gestures](desktop-touch-gestures.md) for one-finger panning, double tapping and second-touch dragging. The proposed visual redesign of the left/right mode controls and the display selector are not implemented by these changes.

## Chat and background behavior

Opening General and choosing Chat creates a local chat window. Start chat is explicit and asks the selected device to open its browser; it does not prove human presence. Minimize retains the local draft and history in a movable in-app bubble. The bubble can be restored or dragged to the close target. These are in-app bubbles, not overlays over other apps.

Open chat sessions are counted by the existing keep-alive foreground service alongside desktop, terminal and files. Backgrounding alone does not deliberately close the chat. The active-sessions notification can remain when only a chat is open and requests a return to the app when tapped. Closing the last eligible session allows the service to stop; other eligible sessions keep it active.

Closing a chat, disposing its authenticated host, losing verified authority or removing the app task can close the session. There is no automatic reopening of the remote browser or replay of chat messages. A foreground service is not persistence after process death. Drafts and chat history are not saved to disk. Chat is text-only, with bounded local history and no delivery or read receipts.

## Appearance

Status-bar icon contrast is selected from the app background and reapplied after configuration layout, on resume and on focus restoration. Rotation behavior must be checked in both light and dark themes without opening the notification shade as a workaround.

## Acceptance checklist

- Check confirmation enabled by default, cancellation, dismissal, and confirmation with and without Do not ask again.
- Restart the app, verify that the preference is retained, then restore confirmation from Settings.
- Verify direct disconnection when confirmation is disabled and verify the existing terminal-history behavior when sessions close.
- Check the new confirmation and Settings text in English and Spanish.
- Run lintDebug after adding the explicit values-en resources; do not treat a source change as a successful lint run.
- Open desktop in the app, add a browser viewer, then repeat in the reverse order; verify image delivery and live counts when either viewer leaves.
- Repeat with multiple connections from the same account and with three viewers.
- Rotate, drag and collapse/expand the desktop toolbar; verify viewport and pointer alignment.
- Keep only a chat open, background the app, receive a remote message and return through the active-sessions notification. Verify the draft and in-memory history.
- Repeat with minimized chats and with other active session types; close chats and check notification lifetime.
- Check explicit closure, logout, permission/network loss and task removal without automatic message replay.

These are acceptance checks, not a claim that they have all passed. Publication, compilation, unit tests, instrumentation and physical-device/live-server validation are separate evidence.
