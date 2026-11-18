# Device sessions and chat

English | [Español](es/device-sessions.md)

## Device cards

Use the card chevron for General, Events, Details and the agent Console. Availability depends on the authenticated account, current permissions, inventory and remote capabilities. The redundant information icon beside the connection badge is removed; Details remains in the expanded options. Sharing is hidden until its implementation is complete.

## Remote desktop

The connection subtitle shows Connected for one viewer or an unknown count, and Connected, 2 viewing or Connected, 3 viewing for multiple viewers. The total sums the connection counts in server metadata, not just distinct accounts. Disconnecting clears the previous count. Metadata updates do not request a screen refresh.

Desktop resume and refresh are distinct protocol operations. Compression settings carry the frame interval, and changing frame rate updates those settings. There is no periodic refresh loop acting as a heartbeat. Toolbar movement, collapse/expand, physical mouse dragging and viewport retention require validation on the intended Android and remote devices.

## Chat and background behavior

Opening General and choosing Chat creates a local chat window. Start chat is explicit and asks the selected device to open its browser; it does not prove human presence. Minimize retains the local draft and history in a movable in-app bubble. The bubble can be restored or dragged to the close target. These are in-app bubbles, not overlays over other apps.

Open chat sessions are counted by the existing keep-alive foreground service alongside desktop, terminal and files. Backgrounding alone does not deliberately close the chat. The active-sessions notification can remain when only a chat is open and requests a return to the app when tapped. Closing the last eligible session allows the service to stop; other eligible sessions keep it active.

Closing a chat, disposing its authenticated host, losing verified authority or removing the app task can close the session. There is no automatic reopening of the remote browser or replay of chat messages. A foreground service is not persistence after process death. Drafts and chat history are not saved to disk. Chat is text-only, with bounded local history and no delivery or read receipts.

## Appearance

Status-bar icon contrast is selected from the app background and reapplied after configuration layout, on resume and on focus restoration. Rotation behavior must be checked in both light and dark themes without opening the notification shade as a workaround.

## Acceptance checklist

- Open desktop in the app, add a browser viewer, then repeat in the reverse order; verify image delivery and live counts when either viewer leaves.
- Repeat with multiple connections from the same account and with three viewers.
- Rotate, drag and collapse/expand the desktop toolbar; verify viewport and pointer alignment.
- Keep only a chat open, background the app, receive a remote message and return through the active-sessions notification. Verify the draft and in-memory history.
- Repeat with minimized chats and with other active session types; close chats and check notification lifetime.
- Check explicit closure, logout, permission/network loss and task removal without automatic message replay.

These are acceptance checks, not a claim that they have all passed. Publication, compilation, unit tests, instrumentation and physical-device/live-server validation are separate evidence.
