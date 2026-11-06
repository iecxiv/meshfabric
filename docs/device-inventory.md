# Device inventory dialog

The information button to the left of the device action menu now queries the authenticated MeshCentral server over a dedicated, read-only control socket scoped to the visible dialog.

Requests after userinfo authentication:

- `getsysinfo`, full original node ID, `cache: true`, `nodeinfo: true`, unique responseid.
- `getnetworkinfo`, same node ID, unique responseid.
- `lastconnect`, same node ID, unique responseid.

Sources consulted:

- MeshCentral meshctrl.js deviceinfo requests: https://github.com/Ylianst/MeshCentral/blob/master/meshctrl.js
- Official platform field changes: https://github.com/Ylianst/MeshCentral/commit/df5e7937be234bba3d5bb7d04d79d5a6adb02f81
- Independently documented live protocol: https://github.com/d-maggipinto/meshcentral-desktop/blob/main/docs/PROTOCOL.md

Hardware is grouped by platform-independent categories. Nested object paths and array indices remain visible, including unknown fields. Scalar values are preserved without guessing units or timestamp formats. Both netif and netif2 and additional network fields are displayed, including MAC, IPv6 and DNS when present. Missing hardware is not synthesized.

This is stored server inventory, not a forced agent scan or proof of real-time freshness. Returned inventory timestamps are shown when present; the query time is never substituted for the inventory time. Restricted accounts may receive partial data, errors or no response. Each section can arrive independently; the 25-second timeout keeps successful sections and reports missing replies.

The socket is cancelled when the dialog closes, the device destination leaves RESUMED state, or the source connection state changes. Returning to the foreground issues new reads. No remote command, shell, agent console, or write operation is used. The original domain-qualified node ID is not normalized away. Nonmatching node IDs and response IDs are rejected; missing response IDs are accepted only on the dedicated single-device socket. No inventory or credentials are logged or persisted by this feature.

Tests added (not executed in the editing environment):

`./gradlew :app:testDebugUnitTest --tests iecxiv.meshfabric.ui.devices.DeviceInventoryTest`

Build and lint (not executed):

`./gradlew :app:assembleDebug :app:lintDebug`

Manual validation required against a real server: compare Linux and Windows devices with the web Details tab; restricted permissions; offline cached inventory; absent inventory; network loss; rapid close/reopen; background/resume; small screens; long values; successful and failed clipboard writes. The new UI retains server key names rather than claiming identical wording or formatting to the web UI.
