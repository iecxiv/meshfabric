# Agent installation scope

## Implemented entry points

- **Agregar agente / Add agent** belongs to an agent device group. It provides platform-specific downloads, installation commands, Assistant settings and mobile configuration. The reference uninstall options provide files and instructions; the app does not execute installers or shell commands.
- **Invitar / Invite** in a device group creates an agent-installation invitation link for that group.
- **Invitar / Invite** in a device menu opens the same installation-link flow. It resolves the device's received `meshid` in the current actor domain. A `nodeid` is never used as an invitation target. Missing, ambiguous or unauthorized group data does not produce a guessed target.

Invitar does not share temporary access to an existing device and does not expose email invitations. The UI transport accepts `createInviteLink` only; it rejects `inviteAgent`, `createDeviceShareLink`, `deviceShares` and `removeDeviceShare`. Older low-level email helpers are not a second UI invitation mode. Historical upstream audit excerpts remain unchanged.

## Invitation options

| Option | Choices | Protocol values |
| --- | --- | --- |
| Link expiration | 1 hour, 8 hours, 1 day, 1 week, 1 month, Unlimited | `expire`: 1, 8, 24, 168, 720, 0 hours |
| Agents | All available, Windows MeshAgent, Linux MeshAgent, MacOS MeshAgent, MeshCentral Assistant, Android MeshAgent | `agents`: 0, 1, 2, 4, 8, 16 |
| Installation type | Background and interactive, Background only, Interactive only | `flags`: 0, 2, 1 |

Here, one month means 30 days. Installation type is hidden for Assistant and Android. Selecting either clears the previous mode and sends neutral `flags: 0`; switching back does not restore a hidden selection. Generation requires an explicit button press. Opening the dialog or changing a selector does not create a link.

Replies must match the request and group. Timeout or interruption after sending is an unconfirmed outcome, not success or an automatic retry. The local displayed link is invalidated when its relevant options, account, connection or authority context change, or the dialog leaves the foreground. This local invalidation is not server-side revocation of a previously generated installation link.

## Enrollment and downloads

Agregar agente covers Windows x86/x64/ARM64, Linux/BSD commands, the binary architecture selector, macOS installers, Android APK/mobile QR, Assistant's five modes and the reference uninstall options. Server availability and permissions still determine which downloads actually succeed.

Authenticated downloads use the current connection's cookie only against the configured origin, do not follow redirects, reuse the configured certificate policy and enforce a 128 MiB bound. Files are downloaded into private temporary storage before the document picker saves them. Binary headers receive a basic format check; this is not cryptographic signature or authenticity verification. No account cookie is added to copied public links or shell commands.

The mobile QR is encoded locally using the server-provided mobile URL, agent certificate hash and received group identifier. Missing metadata is reported as unavailable instead of inventing configuration.

## Publication and validation state

- Invitar integration: `88eb36cc5d39b9cf4f678724426e967716cc12c5`.
- Agregar agente integration: `2f50fd4a9940fe6c5ba8f54addcec52d0af9c347`.
- Full validation workflow commit: `e4ca5dd102d45a2adf284bfc5ba4fa676a294700`, with application sources unchanged from the enrollment integration.
- Full validation success was reported by the repository owner. The workflow runs a clean build, all JVM unit tests, lint and Android instrumentation without class or annotation filters; it additionally requires the exact 39 feature regression methods. The full XML reports and total suite counts have not been independently reviewed for this documentation update.
- Real-server validation and visual APK review are still pending. See `add-device-invite-acceptance.md`. No merge into `dev` or `main` is authorized by an automated test result.
