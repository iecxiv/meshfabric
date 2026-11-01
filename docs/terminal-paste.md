# Explicit terminal paste

English | [Español](es/terminal-paste.md)

Menu -> Paste from Android -> full scrollable preview -> confirm. Clipboard is read only by that menu action, as plain text without URI coercion. No added Enter; existing newlines may execute commands and require acknowledgement. Limit: 16384 UTF-16 units, rejecting rather than truncating. Unsupported controls, bidi embeddings/isolates and malformed surrogates are rejected. Input uses the bundled xterm public paste API and its bracketed-paste/line-ending behavior.

One dispatch per preview, no automatic retry, no logging or persistence. Dispatch callback is not a remote receipt; timeout is unknown and prompts checking the terminal before another attempt. Cancellation cannot undo dispatched input. Existing command editor, focus, IME, keys, output delivery, resize and recovery are unchanged.

CI: existing Node/JVM tests, policy tests, real xterm WebView single-line/no-extra-Enter, CRLF, bracketed multiline and escape-injection tests; build app and test APK, emulator, then publish. Device checks remain: preview/scroll, acknowledgement/cancel, empty clipboard and disconnect. Clipboard contents and commands are not guaranteed safe.
