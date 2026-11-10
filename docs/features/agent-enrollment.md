# Native agent enrollment

Agregar agente provides Windows, Linux/BSD commands, binary installers, macOS, mobile QR/APK, Assistant and the reference uninstall options. Invitar remains a separate installation-link action, never temporary remote access.

Downloads use the current account cookie only against the configured origin. Redirects are not followed. The configured certificate policy is reused, not weakened automatically. Files are streamed to private temporary storage, limited to 128 MiB, checked for an appropriate binary header and saved only through the user's document picker. Partial files are not presented as complete downloads. The app does not execute installers or shell commands.

The mobile QR is encoded locally with pinned ZXing Core 3.5.4 and contains only server-provided mobile URL, agent certificate hash and received group identifier. Missing configuration is an explicit unavailable state.

Automated tests must pass before publication. Emulator tests do not replace visual APK review or validation against the user's real MeshCentral 1.3.1 server.
