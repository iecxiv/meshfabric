# My Account — MeshFabric

[Español](es/profile-native.md) | [Management](profile.md)

My Account uses native controls rather than the old browser shortcuts. A private authenticated account channel remains separate from other application channels.

## Security and account actions

- Change your password using the existing account dialog and current-password checks.
- Enrol a TOTP authenticator using a manual key and six-digit code, or explicitly confirm its removal.
- List and remove previously registered security keys. Native FIDO2 sign-in and registration of new keys are not implemented; there is no automatic browser fallback for registration.
- View, regenerate or clear backup codes with the corresponding visibility and confirmation controls. Structured code replies preserve reported usage state.
- View server-recorded web-login history; it may not contain app sign-ins.
- Create/list login tokens and revoke an individual token with confirmation.
- Change server-account language and local date/time presentation preferences.

Your own entry under Users has two shortcuts: Change password opens the password dialog directly after navigating to My Account; Manage MFA opens a selector for authenticator, existing keys and backup codes. Opening a shortcut does not change credentials or reset MFA. Navigation waits for readiness, consumes a request once and rejects a mismatched actor or server.

## Notifications

Notification options are synchronized with the account's server web state, preserving unrelated settings and unknown bits. Saving is blocked when the required state is unavailable. Server-account language is distinct from Android application language.

The persistent encrypted local inbox and optional Android alerts support received notices, including configured connectivity and AMT notifications. Reception, Android permission, channel settings and server policy remain separate. Delivery with a stopped process is not guaranteed; sound and group-name preferences affect presentation rather than defining additional notice types.

## Operational limits

Requests are single-flight and are not automatically retried. Confirmation depends on each action's acknowledgement or readback; legacy replies are not all transactionally correlated. Password changes distinguish known success and rejection notifications. Account deletion requires explicit confirmation over HTTPS; an HTTP redirect is not proof of deletion. An ambiguous deletion attempt requires checking the account and signing out as instructed.

Secrets and drafts are not deliberately persisted or logged by these dialogs. Copying is explicit and marks clipboard data sensitive; this is not clipboard encryption. Security dialogs request secure-window handling. Lifecycle changes close or pause the channel. Test results do not establish that every account mutation has been verified on a live server.

## Persistent inbox

Unread notices and local read state are restored only after binding the same server/account. Closing the process or signing out does not delete saved notices; other accounts cannot display them through this inbox. Storage is separate from remembered-session preferences and has no plaintext fallback. It retains all unread and up to 200 read entries, within a 16 MiB save limit; errors preserve the previous file and are shown explicitly. Saving is asynchronous: pending writes are not guaranteed after abrupt process termination. See [Notices](notices.md) for deletion, limitations and acceptance checks.
