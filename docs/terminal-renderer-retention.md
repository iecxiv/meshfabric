# Retained terminal renderer after 40479b0

## Scope

Each terminal session may retain its WebView, xterm instance, output cursor, pending ACK, scroll/alternate buffer and font size across navigation. Compose creates only a FrameLayout host. Detaching does not stop loading or reset delivery. Reattaching does not replay history. Resize scheduling, PTY protocol and JavaScript runtime are unchanged.

A page-owned bridge captures the renderer/session, not a disposed composable. Attachment leases prevent a late disposal of the old host from removing the view from a new host. Output remains driven by the existing bounded journal and parser acknowledgements. There is no guarantee of lossless hidden output if JavaScript is throttled long enough to exceed that journal.

## Ownership and memory

The ViewModel owns a main-thread LRU store with a normal limit of four renderer entries. Attached renderers are never evicted; a transition may temporarily exceed the limit until a host releases. Only detached renderers are evicted. Eviction destroys the WebView but does not disconnect the remote PTY. Reopening an evicted renderer uses the existing journal replay, with its existing limitations for alternate screens and truncated streams. This is not unlimited retention.

A MutableContextWrapper uses the current host context only while attached and returns to application context on detach/destruction. Old Activities are not intentionally retained after release. Stop-session, logout and ViewModel clearing dispose renderers. The renderer removes its parent, cancels its output job, invalidates tokens, removes the bridge and destroys the WebView exactly once. pauseTimers is never called because it would affect other WebViews.

Renderer-process loss is handled by disposing the unusable view and showing a localized error, without automatically reconnecting the remote shell. Reopening creates a new renderer and is not equivalent to restoring a VT snapshot. Embedded navigation is blocked to keep the JavaScript bridge on the local terminal page.

## Verification

Eleven new JVM tests cover renderer-store reuse, isolation, replacement, removal, clearing, LRU, attached-view protection and stale attachment releases. CI must also run the existing JavaScript, pipeline and readiness tests and build the debug APK before publication. These unit tests do not exercise a real WebView or IME.

Device acceptance remains required: run nano, move to Devices and return repeatedly, verify no history replay or new shell, change font size and return, switch between two terminals, rotate and show/hide keyboard, disconnect one session, log out, and test a fifth terminal to observe the documented cache limit. Configuration-change retention depends on the existing ViewModel scope surviving; process death does not preserve the WebView.
