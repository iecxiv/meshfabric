# MeshFabric Android App

English | [Español](README_ES.md)

MeshFabric is an unofficial native Android client for MeshCentral. It provides remote desktop, interactive terminal, file management, device notes, remote actions, and an activity view without embedding the MeshCentral web administration interface. The terminal uses locally bundled xterm.js assets inside an Android WebView.

Availability depends on the MeshCentral server, account permissions, remote agent, operating system, and remote session. MeshFabric is not a replacement for the full MeshCentral administration console.

## Features

### Devices and navigation

- Browse devices grouped by their MeshCentral device groups, with online/offline status and operating-system information.
- Expand or collapse individual groups or all groups, with saved group expansion preferences.
- Search the device list by name or operating system.
- Switch between Devices and Activity using the bottom navigation bar.
- Access desktop, terminal, file sessions, and device actions from each device card.

### Remote desktop

- Native remote desktop (KVM) with touchscreen mouse controls, left/right click, scrolling, zoom, and pan.
- Virtual keyboard and shortcuts such as Ctrl+Alt+Del, Win+R, Alt+Tab, and function keys, subject to the remote environment and OS restrictions.
- Configurable quality, scale, frame rate, and encoding settings.
- Clipboard controls and optional clipboard synchronization, subject to remote-session and server restrictions.
- Settings for mouse-button swapping, wheel direction, remote keyboard mapping, and locking on disconnect.
- Gesture capture is renewed when viewport or fitted-image dimensions change, so the handler no longer retains pre-rotation geometry. Test clicks, long presses, and scroll after rotating on the intended device.

#### Software and physical keyboard input

Native Android key-down/key-up routing supports software keyboards that emit key events and physical keyboards. The mapping includes arrows, navigation, Tab, Enter, Escape, Backspace/Delete, Insert, Home/End, PageUp/PageDown, F1-F12, letters, digits, keypad keys, OEM punctuation, and modifiers when Android delivers them to the app.

- Mapped events are consumed before the hidden text field handles them, preventing those events from also becoming local caret movement or duplicate text edits.
- Hardware input capture does not require opening the on-screen keyboard.
- Repeated key-down events and balanced modifier handling are supported.
- Input capture is suspended while desktop dialogs are open or the screen is inactive. Tracked keys are released best-effort when focus or input ownership is lost, without replaying them into another session.
- IME text callbacks and Unicode transport remain separate from key-event routing.

This targets problems such as arrows from Hacker's Keyboard not reaching the remote desktop, but universal keyboard compatibility is not claimed. Automated tests simulate soft-keyboard-flagged events; they do not run the actual Hacker's Keyboard APK. Physical-device and remote-host verification is still required. Android-reserved keys, remote layouts, AltGr, lock/keypad state, sided modifiers, IME composition, mixed toolbar input, games/raw scan-code input, and secure OS shortcuts have limitations. A release sent after a transport has already failed cannot guarantee that the remote OS receives it.

See [Desktop keyboard input](docs/desktop-keyboard-input.md).

### Terminal

- Interactive terminal through the MeshCentral agent relay, with font scaling and a retained local WebView/xterm renderer.
- Explicit on-screen keyboard control: the top keyboard icon shows or hides the terminal keyboard. The command editor remains available from the terminal tools menu.
- App-originated terminal focus requests are blocked while menus or dialogs own input. Closing them restores terminal focus without automatically reopening the keyboard.
- Stale focus requests are cancelled across window-focus loss, backgrounding, detachment, and destruction.
- Editor flags request that compatible IMEs avoid fullscreen/extract editing and personalized learning. IMEs may ignore these requests.

#### History panel

- The History icon sits between A+ and the keyboard icon. Terminal header controls use compact 36 dp layouts.
- Pressing History with the panel hidden shows it expanded; pressing it while visible hides the entire panel. The icon is colored only while the panel is visible and does not own a separate dropdown.
- An involuntary disconnection of an established terminal connection shows the panel collapsed, once per incident. Restoring saved history or explicitly closing with X does not trigger this automatic presentation.
- The panel's own arrow expands or collapses its contents. Header and contents share one full-width surface, background, and rounded border.
- Reconnect, Return to current terminal, or Cancel appear in the panel header according to state, without duplicate entries in the expanded list.
- Previous histories can be opened read-only and deleted individually or together. Deletion requires confirmation through the standard app dialog and excludes the current session.

#### Selection and copying

Use the terminal tools menu to open Select and copy. This opens a native, read-only snapshot dialog rather than selecting directly over the live terminal canvas.

- Long-press text in the dialog to select a range and use the native copy toolbar.
- Copy all copies the captured snapshot.
- Snapshot extraction joins soft-wrapped rows, preserves real line breaks, and excludes blank viewport padding.
- Capture is bounded to 2,000 recent rows and 200,000 UTF-16 code units. Truncation is shown explicitly, and UTF-16 surrogate pairs are not split.
- The active buffer is captured; a full-screen application does not expose the hidden normal buffer through this action.
- Copying is user-initiated and does not send input to the remote shell.

#### Pasting and editing before sending

The terminal tools menu provides:

- Paste from Android: read plain clipboard text, review it, then confirm.
- Paste with editing: preload the accepted clipboard text into an editable multiline field before sending the edited draft.

Opening a paste dialog or editing its text does not send it. Paste actions do not append Enter, but existing line breaks can still execute commands. Multiline input requires acknowledgement; changing accepted edited text resets that acknowledgement.

Paste input is limited to 16,384 UTF-16 code units. Oversized input, unsupported control characters, and malformed Unicode are rejected rather than silently truncated. Invalid edits retain the previous draft and show an error. Sending uses xterm's public paste API and its bracketed-paste/line-ending behavior; this is not a guarantee that the remote application handles pasted commands safely.

Each preview permits one dispatch attempt, without automatic retries. An accepted local dispatch is not a remote receipt or execution confirmation. If dispatch cannot be confirmed, inspect the terminal before trying again. Cancelling cannot undo input already sent. The separate command editor keeps its command-submission behavior and should not be confused with the paste actions.

#### Special keys and scrolling

- Compact default row: Esc, Tab, Ctrl+C, Enter, and Backspace.
- Expand with the downward chevron; collapse with the same icon inverted.
- Additional rows provide arrows, Home/End, Insert/Delete, PgUp/PgDn, Ctrl shortcuts, and a Ctrl+A through Ctrl+Z picker.
- Keys use a filled, small-corner style. Their visible height is compact at normal font scale, while minimum interaction space is retained; larger font settings can increase height to avoid clipping.
- Arrow symbols use one consistently sized vector icon rotated by direction. Enter and the latest-output symbol remain enlarged; End uses the same typography as Home.
- Cursor keys honor xterm's application-cursor mode. Backspace retains byte 8, and Enter sends CR.
- PgUp/PgDn/Home/End are remote keys. The separate latest-output control scrolls the local normal-buffer history to the bottom without sending keys to the remote host. It is unavailable in the alternate buffer.
- Touch scrolling over rendered text is routed to the native viewport on coarse-pointer devices in the normal buffer when mouse reporting is inactive. Alternate-buffer and mouse-reporting behavior is retained.

While touch-scroll pass-through is active, direct canvas selection and canvas link taps are not preserved. Use the Select and copy dialog for text selection. No universal behavior across all full-screen terminal applications or IMEs is claimed.

#### Disconnection and manual reconnection

- A network change or tunnel failure closes the transport without clearing current history or deliberately resetting the retained view's scroll position. The view stays available for reading and copying.
- The history panel reports disconnected/read-only state outside the remote output. Typing, paste, remote keys, and keyboard requests remain blocked while disconnected, even if the panel is manually hidden.
- Reconnect allows one candidate attempt per device, with cancellation and a total 30-second deadline covering control-channel readiness and terminal negotiation. Opening a WebSocket alone does not count as a successful terminal connection.
- Failure, timeout, or cancellation closes the candidate and leaves the previous session record and output intact. There are no automatic terminal retries and no queued commands to resend.
- After successful negotiation, a new independent terminal session becomes current. Previous history remains separately available; its output journal and callbacks are not reused by the new session. A clear-screen sequence from the new session cannot clear previous history.
- The red X explicitly closes and archives the current terminal, cancels pending reconnection, and returns to Devices. The device's Terminal button returns to its normal unstarted appearance. Back navigation alone does not explicitly close the session.
- Reconnection does not establish that the original remote process survived or resumed. Shells and full-screen applications such as nano are not guaranteed to continue. Recreate view is a different action and does not reconnect the transport.

#### History, renderer retention, and recovery

- Closing a terminal, including closing all current sessions, archives its record without leaving a current-session highlight. Archives are separate from the current-session registry.
- With Save terminal buffers enabled, closed records and active-output checkpoints are persisted per server/account and restored as read-only histories. Output checkpoints are throttled to approximately one second, with additional saving on activity stop and archival through the recent-task-removal callback. Abrupt termination can still lose output not yet written.
- With the setting disabled, archives remain in memory only. Disabling it removes saved archive and legacy buffer files. Deleting an archive also deletes its saved file so it does not reappear after restart.
- See [Terminal archive persistence](docs/terminal-archive-persistence.md) for storage, account separation, legacy migration, and recovery limits. Persisted output does not recreate a live terminal connection.
- Retained renderers preserve local state across navigation while they remain cached. The cache retains up to four renderers, without evicting attached views. An evicted renderer may be recreated from the available output journal, not from a full VT snapshot.
- Truncated retained history is identified explicitly. Rebuilding an initial disconnected view may read from the oldest output still available; this is best-effort history replay, not restoration of missing output or complete terminal visual state.
- Live output delivery keeps strict gap detection, including its initial read. Continuing from an evicted or invalidated cursor does not silently skip missing output.
- Renderer loss and foreground parser-acknowledgement timeouts have an explicit, confirmed Recreate view action. Recreating a view does not restart the remote shell and may require full-screen applications to redraw.
- Retained history does not prove that a tunnel is connected or that the original remote process has resumed. Process death does not preserve the WebView.

Implementation notes:

- [Selection and copy](docs/terminal-selection-copy.md)
- [Paste preview](docs/terminal-paste.md)
- [Paste with editing](docs/terminal-paste-edit.md)
- [Focus and IME](docs/terminal-focus-ime.md)
- [Special keys](docs/terminal-special-keys.md)
- [Touch scrolling](docs/terminal-touch-scroll.md)
- [Renderer retention](docs/terminal-renderer-retention.md)
- [ACK watchdog and view recovery](docs/terminal-ack-recovery.md)
- [Manual reconnection and retained history](docs/terminal-manual-reconnect.md)
- [Archive persistence](docs/terminal-archive-persistence.md)

### File manager and text editor

- Browse remote directories and navigate to a specific path.
- Upload files and download them to Android.
- Create folders and text files; copy, cut, paste, move, rename, and delete items.
- ZIP compression and supported archive extraction, with conflict/overwrite dialogs and transfer progress controls.
- Edit small candidate text files using a monospaced editor with logical line numbers, word wrap, and scrolling. Wrapped continuation rows do not create additional logical line numbers.
- The shared editor is also used for device notes. Syntax highlighting is not currently implemented.
- File operations currently work on individual selected items; multi-file selection is still planned.

### Device notes and remote actions

The per-device action menu includes:

- Read notes and edit/save notes.
- Run a command or script with operating-system-aware interpreter selection.
- Send a remote notification.
- Request that the remote device open an HTTP/HTTPS URL.
- Wake a device using Wake-on-LAN (WoL).
- Restart, shut down, or suspend a device.

Notes support local drafts, confirmation before discarding edits, and save verification by reading the notes back from the server. The notes editor currently limits content to 64000 characters. Saving an empty field removes the existing notes.

Remote command output is displayed separately from an interactive terminal. Closing the output window does not stop the remote command, and receiving output is not proof of successful process completion.

A request being sent or accepted is not the same as an action being executed. MeshFabric reports unconfirmed results and connection interruptions rather than automatically repeating potentially disruptive actions. Remote notification display, URL opening, power actions, and WoL depend on the server and remote environment.

### Activity

- View recent local action requests, server events, errors, and available command output.
- Search activity and filter by category or device, with entries grouped by date.
- Inspect request identifiers, actor information, timestamps, and result details when available.
- The activity journal retains up to 200 entries in memory. It is a session-level view, not a persistent or complete server audit-log archive.

### Background sessions and notification

- A keep-alive foreground-service notification is used while the app is in the background with sessions that need it.
- Tapping the notification requests a return to MainActivity through an explicit immutable activity PendingIntent. It does not use task-clearing flags or explicitly request a terminal disconnect/restart.
- Returning to the foreground follows the app's lifecycle behavior. A dead process still requires normal app startup; the notification does not guarantee recovery of an earlier live remote session.

### Appearance and language resources

- Material 3 interface with system, light, and dark theme modes.
- Custom app display name and icon, plus a setting to hide the settings icon.
- English and Spanish Android resource files are provided, including terminal reconnection, history-panel, deletion, close, and history-header controls.
- Localization is not complete: some native management, activity, desktop, and feedback messages remain hard-coded in Spanish. Resource-key coverage is not proof that every visible phrase has been externalized or translated.

The localization inventory and its limitations are documented in [docs/i18n](docs/i18n/README.md).

## Requirements

- A reachable MeshCentral server and a valid account.
- Devices/agents that expose the requested remote functionality and the necessary server-side permissions.
- Android 8.0 or newer (API 26 minimum).

Build configuration:

| Setting | Value |
| --- | --- |
| Minimum SDK | `26` |
| Compile SDK | `35` |
| Target SDK | `35` |
| Java source/target compatibility | `17` |

The build configuration is defined in `app/build.gradle.kts`.

## Build from source

1. Clone the repository:

   ```bash
   git clone https://github.com/iecxiv/meshfabric.git
   cd meshfabric
   ```

2. Open the project in an Android Studio installation compatible with the Android Gradle Plugin and Gradle wrapper configured in this repository. Install Android SDK Platform 35 and let Gradle synchronize the project.
3. Select a Gradle JDK compatible with the configured build tooling. Verification tasks use JDK 21; Java source/target compatibility remains 17. These are separate settings.
4. Build and run on an Android device or emulator using API 26 or newer.

To build a debug APK from the repository root:

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
.\gradlew.bat :app:assembleDebug
```

The generated debug APK is placed under `app/build/outputs/apk/debug/` after a successful build. Build tooling is defined in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`.

## Verification

Run JVM tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run the standalone terminal JavaScript checks with Node.js:

```bash
node app/src/test/js/terminal-runtime.test.js
node app/src/test/js/terminal-selection.test.js
node app/src/test/js/terminal-keys.test.js
```

Build and run Android instrumentation tests with a connected device or running emulator:

```bash
./gradlew :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

Verification includes key mapping/modifier state, Compose input dispatch and key-bar checks, bundled-xterm echo/copy/paste behavior, terminal focus/IME handling, and touchscreen scrolling. Unit coverage also includes terminal attempt ownership, stale completions, strict live reads versus explicit history replay, panel-opening policy, and archive storage behavior.

History-panel instrumentation tests cover hidden content, duplicate primary-action labels, and expansion through the panel arrow. Compiling these tests does not mean they were executed on a device. JVM tests and a successful debug build do not by themselves verify zoom/rotation click alignment or notification tap behavior on a physical device.

Some feature changes use CI publication workflows; a workflow commit is not itself the functional change. Check that the functional commit was published and inspect the outcome of its validation run.

Passing automated tests does not establish universal compatibility with Hacker's Keyboard, every physical keyboard, every IME, all Android environments, or all Windows/Linux hosts. Test real typing, shortcuts, locale/accents, composition, repeats, focus changes, reconnection, font scaling, and landscape/keyboard layouts on the intended devices.

## Technology

- Kotlin and Jetpack Compose with Material 3.
- MVVM-style view models, Kotlin Coroutines, and StateFlow.
- OkHttp WebSockets and HTTP connections for MeshCentral control and relay channels.
- Locally bundled xterm.js for terminal rendering and input handling.
- AndroidX Security Crypto for encrypted session preferences when initialization succeeds.

## Security and operational notes

- Remembered session data includes the server URL, username, and authentication cookie. The session persistence code does not save the login password.
- Session preferences use `EncryptedSharedPreferences` with an Android KeyStore-backed master key when available. The implementation falls back to ordinary app-private `SharedPreferences` if encrypted initialization fails; do not assume that remembered session data is always encrypted.
- Saved terminal output is not encrypted. Archive records use JSON files in app-private no-backup storage, separated by hashed server/account keys. Disabling Save terminal buffers removes saved archives and legacy buffer files; history can remain in memory for the current execution. See [Terminal archive persistence](docs/terminal-archive-persistence.md).
- Terminal copying marks clipboard content as sensitive to suppress supported Android clipboard previews. This is not clipboard encryption or a guarantee against other authorized readers.
- Treat clipboard data, retained output, and diagnostic logs as potentially sensitive. Do not assume that every diagnostic path has been audited for content disclosure.
- The option to ignore SSL certificate errors weakens certificate verification. Prefer a trusted server certificate and leave this option disabled outside controlled environments.
- Account permissions, server policies, and agent capabilities remain authoritative. An available button or a submitted request does not override server-side authorization.
- Review targets and payloads before sending commands or power actions. Check the remote device before repeating an action whose result is unknown.

## Roadmap and known gaps

The following items are not presented as completed features:

- User administration, user-group administration, and permission-management screens.
- More complete effective-permission handling in the client interface.
- Device/session sharing.
- Advanced hardware/software inventory.
- Advanced device filters and favorites, beyond the existing device search and activity filters.
- Inventory/activity export.
- YubiKey integration.
- Multi-file selection and bulk file operations.
- Automatic terminal reconnection, advanced terminal-state restoration, and broader reconnection validation. Retained output does not restore or prove continuity of the original remote process.
- Completion of English/Spanish localization for native management features.
- Broader real-device desktop keyboard, layout, AltGr, IME, rotation/zoom, and notification-navigation validation.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
