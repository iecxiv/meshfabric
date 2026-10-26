# MeshFabric Android App

MeshFabric is an unofficial native Android client for MeshCentral. It provides remote desktop, interactive terminal, file management, device notes, remote actions, and an activity view without embedding the MeshCentral web interface.

The features below describe the implementation currently present in `main`. Availability depends on the MeshCentral server, account permissions, remote agent, operating system, and remote session. MeshFabric is not a replacement for the full MeshCentral administration console.

## Current features

### Devices and navigation

- Browse devices grouped by their MeshCentral device groups, with online/offline status and operating-system information.
- Expand or collapse individual groups or all groups, with saved group expansion preferences.
- Search the device list by name or operating system.
- Switch between Devices and Activity using the bottom navigation bar.
- Access desktop, terminal, file sessions, and device actions from each device card.

### Remote desktop

- Native remote desktop (KVM) with touchscreen mouse controls, left/right click, scrolling, zoom, and pan.
- Virtual keyboard and shortcuts such as Ctrl+Alt+Del, Win+R, Alt+Tab, and function keys.
- Configurable quality, scale, frame rate, and encoding settings.
- Clipboard controls and optional clipboard synchronization, subject to remote-session and server restrictions.
- Settings for mouse-button swapping, wheel direction, remote keyboard mapping, and locking on disconnect.

### Terminal and session history

- Interactive terminal through the MeshCentral agent relay, with font scaling, control-key shortcuts, and quick input helpers.
- Disconnected terminal sessions retain their output for consultation and show a disconnect banner after a network change or tunnel failure.
- Optional persistence of terminal buffers across app restarts.
- Explicitly closing or restarting a terminal session clears its previous buffer. A retained buffer does not mean that the remote process or tunnel is still connected.
- Automatic terminal reconnection that preserves the previous history remains a separate development item; disconnected history is not automatically reattached to the old process.

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
- The current activity journal retains up to 200 entries in memory. It is a session-level view, not a persistent or complete server audit-log archive.

### Appearance and language resources

- Material 3 interface with system, light, and dark theme modes.
- Custom app display name and icon, plus a setting to hide the settings icon.
- English and Spanish Android resource files are provided. Localization is not yet complete: some native management, activity, and feedback messages in `main` are still hard-coded in Spanish.

## Requirements

- A reachable MeshCentral server and a valid account.
- Devices/agents that expose the requested remote functionality and the necessary server-side permissions.
- Android 8.0 or newer (API 26 minimum).

The current app configuration declares:

| Setting | Value |
| --- | --- |
| App version name | `1.0.7` |
| Minimum SDK | `26` |
| Compile SDK | `35` |
| Target SDK | `35` |
| Java source/target compatibility | `17` |

These values come from `app/build.gradle.kts`; the version name is not a claim that a corresponding release APK has been published.

## Build from source

1. Clone the main branch:

   ```bash
   git clone --branch main https://github.com/iecxiv/meshfabric.git
   cd meshfabric
   ```

2. Open the project in an Android Studio version compatible with the Android Gradle Plugin and Gradle wrapper configured in this repository. Install Android SDK Platform 35 and let Gradle synchronize the project.
3. Select a Gradle JDK compatible with the configured build tooling. Java source compatibility and the Gradle runtime requirement are separate settings.
4. Build and run on an Android device or emulator using API 26 or newer.

To build a debug APK from the repository root:

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

The generated debug APK is placed under `app/build/outputs/apk/debug/` after a successful build. Build-tool versions are defined in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`.

## Technology

- Kotlin and Jetpack Compose with Material 3.
- MVVM-style view models, Kotlin Coroutines, and StateFlow.
- OkHttp WebSockets and HTTP connections for MeshCentral control and relay channels.
- AndroidX Security Crypto for encrypted session preferences when initialization succeeds.

## Security and operational notes

- Remembered session data includes the server URL, username, and authentication cookie. The session persistence code does not save the login password.
- Session preferences use `EncryptedSharedPreferences` with an Android KeyStore-backed master key when available. The current implementation falls back to ordinary app-private `SharedPreferences` if encrypted initialization fails; do not assume that remembered session data is always encrypted.
- Saved terminal buffers are text files in app-internal storage and are not encrypted by the buffer implementation. They may contain sensitive command output; buffer persistence can be disabled in settings.
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
- Terminal reconnection workflows that preserve previous output without implying that an old remote process has resumed.
- Completion of English/Spanish localization for native management features.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
