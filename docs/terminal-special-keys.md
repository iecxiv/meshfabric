# Special keys and history comfort

English | [Español](es/terminal-special-keys.md)

Default row: Esc, Tab, Ctrl+C, Enter, Backspace. Expand navigation/editing keys, Ctrl picker A..Z, Ctrl+Z/X/O/W and fixed latest-output control. Buttons reserve at least 48dp per dimension; rows scroll horizontally. Ctrl picker sends one chosen shortcut, not a sticky IME modifier. Offline input is disabled, but healthy retained history remains available.

Use bundled xterm public input API and existing onData/native bridge. Arrow/Home/End honor applicationCursorKeysMode. Preserve Backspace byte 8 and Enter CR. No hold-repeat or retries. PgUp/PgDn/Home/End are remote keys. Latest output is local scrollToBottom in the normal buffer only, with zero input; alternate buffer gets localized feedback. Popup focus uses the established overlay gate; deliberate shortcut dispatch is allowed while it closes. No scroll CSS, physics, sensitivity, output-follow policy, focus-machine, IME flags, command-editor or version changes (1.1.1/code 1).

CI: encoding checks, Compose size/expand/disabled/single-dispatch tests, real bundled-xterm normal/application sequences and input-free history tests; existing focus, echo, copy, paste, touch regressions. Device checks remain for font scaling, landscape/IME space, remote editors and perceived layout. Dispatch is not a remote execution receipt.
