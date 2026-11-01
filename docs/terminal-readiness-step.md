# Terminal: readiness step after validated phase 1

English | [Español](es/terminal-readiness-step.md)

This step does not reintroduce the rejected wire-protocol phase 2.

## Changes

- Kotlin no longer initializes history or starts its live collector 150 ms after onPageFinished.
- Only the runtime's onHtmlReady signal can initialize the view.
- A page-generation gate and exact WebView identity check reject late callbacks from disposed/replaced views.
- Repeated readiness signals initialize a page at most once.
- Disposal invalidates the page token without changing the existing session retention policy.

## Unchanged

The phase 1 JavaScript runtime, its 120 ms resize coordinator, the Kotlin relay/protocol/UTF-8 behavior, terminal shortcuts and command dialog are unchanged.

## Limitations

This is initialization hardening, not the complete lifecycle refactor. WebView retention, renderer-process recovery, the raw ANSI history replay, and the atomic history/live-stream handoff remain unresolved. SharedFlow saturation is not fixed. No claim of lossless output or nano stability beyond the user's phase 1 validation is made.

## Validation

Six JUnit cases are supplied for the generation gate. They require execution in the Android project; the generator environment has no Kotlin/JVM compiler. The Python applicator is tested separately using disposable local Git repositories. These checks do not compile Kotlin or exercise Android WebView.

Device checks: cold-open terminal, navigate away/back rapidly, repeat with nano running, show/hide keyboard, rotate, change font size, and verify shortcuts/command dialog. No forced terminal reconnect is introduced by this step.
