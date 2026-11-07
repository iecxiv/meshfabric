# Native My user: second delivery

This replaces browser shortcuts in My user. Users and device-group management remain native.

Implemented: TOTP manual-key enrollment and removal; backup-code listing, regeneration and clearing with confirmation; registered security-key listing/removal and readback; previous web-login history with local time formatting; password change acknowledged by server notification ids; token creation/list/revocation; server-account language with userinfo readback; account-scoped local 12/24-hour and foreground notice preferences; native HTTPS account-deletion request with full-ID and repeated-password confirmation.

Explicit limitations:
- New FIDO2/WebAuthn security-key registration is NOT implemented. Listing/removing existing keys is native. No browser is opened as a fallback.
- Notification settings are LOCAL to My user while that view is open. They neither enable Android background push nor change MeshCentral account/device notification preferences.
- Time-format preferences affect this view's history only. The language action changes the server account language, not Android/app-wide language or the saved browser state.
- Account-deletion HTTP replies are ambiguous. The UI NEVER declares deletion confirmed from a redirect. After an attempt it closes the account channel, reports an unconfirmed result and instructs the user to sign out via Settings. It does not claim that other sessions were revoked or automatically clear all app credentials.
- Backup-code object formats other than primitive string/number arrays are rejected rather than displayed as usable codes.

Transport: a private authenticated control channel, single-flight requests, action-specific validation, response-id checks when supplied, 20-second timeout closing the channel, no write retries, lifecycle cancellation, source identity/user-ID binding. Password changes accept only notification 20 as success and 17/18/19/21 as rejection. Userinfo readback confirms language; key-list readback checks removal. Secrets/drafts are memory-only; copying is explicit and marked sensitive; profile/account dialogs request SecureOn. No raw protocol logs are added. This is not a claim of transactional confirmation for legacy replies lacking request ids.

Three policy tests are included. Android compilation, tests, lint, physical-device behavior and live-server account mutations have NOT been run here. Real-server verification is required before treating this delivery as validated.
