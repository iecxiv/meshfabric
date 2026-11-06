# Native version check and guarded update

The existing General action opens a native version dialog instead of the generic JSON viewer. The dialog obtains the current source through the same LocalDeviceDetailsClient used by the My Server entry point. If no client is provided it fails closed. It performs its own fresh version/permission query; the initial read-only request is still made by the parent reader. This is intentionally redundant, not an update.

Current, stable and latest are read from serverversion.tags. Missing or malformed values remain unavailable. Only concrete numeric three-part versions with optional prerelease/build suffixes are accepted, not npm aliases, URLs, options or arbitrary command text. An installed target is shown as already installed. No current version means update actions remain disabled.

The user reviews a separate confirmation identifying the server origin/domain path (query credentials stripped), current version, selected channel and exact version. It also displays the exact serverupdate packet. An available stable version may be older than the installed latest/prerelease: this is explicitly warned about, not silently presented as necessarily newer.

The channel captures URL, web cookie and TLS preference. It requires HTTPS, validates update site right 16, and sends another userinfo query after confirmation before the one serverupdate request. Identity and the reviewed version snapshot must still match. The server remains the final authority for domain restrictions, disabled updates, npm availability and update failure. Certificate-validation bypass is visibly warned about when already configured.

A queued message or a server reply never proves installation/restart. Timeouts, backgrounding and disconnects after an attempt are unknown. No reconnect, foreground transition or refresh automatically resends an update; the channel permits only one attempt during the dialog lifetime. Closing cannot undo server work. State is memory-only; reopening is an explicit new user flow and does not prove a prior attempt failed. Verify the server before repeating an uncertain attempt.

No credentials or response bodies are added to diagnostic logs. Five unit tests are added but not executed in the editing environment. No live update, restart, build or visual/device validation was performed. Header/tab integration and xterm-style server console remain separate pending work. App version is unchanged.
