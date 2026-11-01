# Interaction polish 1: selection and copy

English | [Español](es/terminal-selection-copy.md)

Adds a terminal tools menu and a native read-only snapshot dialog. Long-press selection uses Compose SelectionContainer, not DOM selection over the terminal canvas. Partial selection copies through the native selection toolbar; Copy all copies exactly the snapshot. Copying is always user-initiated and never sends input to the remote shell. This is a separate snapshot, not direct selection on the live terminal.

Snapshot extraction uses only the active xterm buffer public line APIs. Physical soft wraps are joined; genuine line breaks remain. Blank viewport padding is omitted. Bounds are 2,000 recent rows and 200,000 UTF-16 code units; truncation is shown explicitly, and surrogate pairs are not cut. There is no capture from the hidden normal buffer while nano uses the alternate buffer.

The native read is cancellable and times out after three seconds. Closing the dialog cancels its coroutine, so late callbacks cannot reopen or repopulate it. The text is transient UI memory, never logged or persisted. Selection does not pause the terminal or alter its delivery cursor, resize or protocol.

Both partial and whole-snapshot copying use a dialog-local ClipboardManager that marks content sensitive to suppress the Android clipboard preview. This does not encrypt the clipboard or prevent other authorized applications from reading pasted text. Android 13+ provides its own copy feedback; only older versions get a Toast.

Verification: new pure JavaScript snapshot tests plus existing runtime tests. The Android WebView cases also verify snapshot text; an ANSI-color case checks that copying returns rendered characters rather than escape sequences. Another instrumentation case checks ClipData text and sensitive metadata. CI must build app/tests and run JVM, Node and focused Android tests before publishing. Physical-device verification of long-press handles, selection scrolling, layout in landscape, clipboard toolbar and retained terminal state remains necessary.

Paste, IME/focus behavior and special-key/scroll redesign are deliberately excluded from this commit.
