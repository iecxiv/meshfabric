# Windows cursor anchoring

Keep the Windows windowsPty row-growth compatibility in place. Scope the viewport adjustment through the existing native Windows-only bridge, exactly as the compatibility script already does.

For a normal buffer, a focused vertical shrink with unchanged columns is treated as keyboard opening: return to the cursor even from historical scrollback. This is a geometry/focus heuristic, not a native IME visibility callback; another focused, same-column shrink can also activate it, and a keyboard transition that changes columns may not. Device validation remains necessary.

Compute the viewport target from the public buffer API: max(0, baseY + cursorY - rows + 1). scrollToLine clamps it to the available range. This places the cursor near the bottom where the buffer permits it, without changing the buffer, deleting blank rows, injecting terminal input or guessing the prompt text. On subsequent resize, preserve cursor-following when it was already active or the viewport was at the live bottom. Otherwise leave history positioning to xterm.

While anchored, realign after write parsing AND after the runtime callback, since that callback can scrollToBottom. Cancel anchoring on pointerdown, touchmove or wheel in the terminal; subsequent output does not reactivate this adjustment. A later keyboard-like shrink can reactivate it as requested. Do not anchor alternate-screen applications. Remove DOM listeners on dispose.

Run:

    node app/src/test/js/terminal-windows-pty.test.cjs
    node app/src/test/js/terminal-windows-anchor.test.cjs

The anchor suite uses a mock Terminal. It checks historical-scroll to cursor on focused shrink, callback receiver/return preservation, adjustment after a simulated runtime scroll, manual cancellation, resize growth, unfocused history, alternate buffers, idempotence, disposal and no wrapping without the Windows bridge. It does not execute bundled xterm or compile the APK.

Device acceptance: repeat keyboard cycles from the bottom and from several historical positions; opening must reveal the cursor, and manual scrolling afterwards must remain usable. Close the keyboard while reading history and check that it is not forced to the cursor. Recheck all 200 numbered lines in Copy All, rotation, font changes and alternate-screen applications. Verify Linux and unknown nodes retain their prior behavior. Capture MeshTerminalWinResize if the cursor is still misplaced; this patch does not prove or address Android window-panning outside xterm.
