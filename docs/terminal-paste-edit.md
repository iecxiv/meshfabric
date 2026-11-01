# Paste with editing

English | [Español](es/terminal-paste-edit.md)

The terminal tools menu keeps Select and copy and Paste from Android, adding Paste with editing as a third entry. It reads the clipboard only on that explicit action and preloads the accepted plain-text snapshot into a multiline editor. Empty or rejected clipboard input starts with an empty editor and a visible reason.

Only Send text dispatches the current edited draft through the existing paste path, with no added Enter. Enter inside the editor inserts a line break; it is not a send action. Existing multiline acknowledgement is keyed to the current text, so changing accepted text resets it. Invalid edits are rejected as a whole, retaining the last draft and showing an error; confirmation stays disabled until a valid edit. There is no silent truncation. Closing discards the draft; no clipboard overwrite, logging or persistence.

Existing paste bounds, control/Unicode validation, single-attempt guard, renderer checks, bracketed-paste handling and unknown-result/no-retry behavior are reused. The read-only paste flow and terminal output/transport are unchanged. CI builds app and test APK, runs all JVM/Node tests and existing WebView echo/copy/paste regressions. Device verification remains necessary for editor/IME, edited payload, cancel, acknowledgement reset and dialog layout with keyboard.
