# Management — MeshFabric

[Español](es/profile.md) | [Project README](../README.md)

Management replaces the former Profile label. Its four tabs are My Account, Device Groups, Users and Groups (user groups). Device groups and user groups are different entities.

## Available functionality

- Agent-group actions provide Add agent and installation-link invitations: see [Agent installation and invitations](agent-installation.md).

- Native account-security and preference controls: see [My Account](profile-native.md).
- Native users, user groups, memberships and device-group access: see [User and access administration](profile-user-administration.md).
- Device-group details include summary, devices and received access. Advanced settings cover supported flags, consent and inactivity expiry.
- User details link to received memberships, common device groups and visible devices. Navigation uses exact internal identifiers and available records, not names alone. Returning restores a valid originating view when possible.
- Connection indicators and outer spacing are consistent across the four Management and four Server tabs. Each indicator reports its own channel; it is not a statement that every channel is connected.

## Search and navigation

Directory search opens and closes from the header. Its input is limited to 256 characters. Switching Management sections and Back navigation are covered by full-screen integration tests, including a font scale of 1.8.

Inside a device group, device search filters that group's devices rather than the full server inventory. Each opened group retains its own query across trips back to the directory, whose filter is preserved separately. Search is hidden on the group's Access and Summary tabs; returning to Devices restores its query. With group device search open, Back first closes that search; a subsequent Back returns to the directory.

Signing out preserves local application preferences. This is distinct from retaining authenticated account data or restoring an active session.

## Safety and scope

Server permissions and policy remain authoritative. Missing records or failed reads mean unavailable data, not an empty inventory or proof of deletion. Common-access views are based on received visible data, not a complete effective-permission calculation.

Sensitive access changes use reviewed data and separate confirmation. Changed identity, permissions, data or connection can invalidate the review. Unknown outcomes are not success and writes are not automatically retried. Reads and writes are not atomic; concurrent administrator changes remain possible.

Native FIDO2/WebAuthn sign-in and security-key registration, listing and removal are implemented. The user reports successful physical registration, removal and sign-in, including USB sign-in, for the key used in this work. See [FIDO support and validation scope](fido/README.md). These checks do not establish universal compatibility or validation of every account operation.

## Background/resume continuity

Temporary loss of readiness no longer clears the parent dialog navigation solely for that reason. Previous presentation data can remain visible with actions blocked until fresh matching account data is available. Identity changes clear the navigation context; sensitive reviews can still invalidate permanently on interruption. This does not promise survival of every nested dialog or restore dialogs after process death. The account-security dialogs keep their own safety rules. See [Notices and continuity](notices.md).

## Validation scope

The maintainer reports both permanent CI jobs passing, with eleven instrumentation classes in the regression suite. Full-screen search tests use controlled session data; they do not establish live-server permissions or event delivery.

Manual checks reported by the maintainer confirm sign-in, Sharing, and returning from the background to remote desktop and terminal in the tested environment. These checks do not establish compatibility with every server, account, Android device or interruption, and do not cover file-manager resume or process-death recovery.

Still to verify against the intended server:

- Accounts with restricted roles, including access removal while a device view is open; compare the same account and device with the server web interface.
- Historical event queries and details, checking identity, timestamps and duplicate-free refresh against records visible in the web interface.
- Inventory changes such as device/group names, visibility and connection state, distinguishing automatic updates from explicit refresh, including after backgrounding.
