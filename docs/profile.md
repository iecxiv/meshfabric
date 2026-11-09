# Profile: initial implementation and limitations

- Profile appears to the right of Server. A full-screen Compose destination preserves the underlying home selection; Back returns there. Labels support Spanish and English.
- Tabs: My user, Users, Device groups.
- Native users: list, create with username/password/email, edit email, delete another account with typed full-ID confirmation. Role editing and administrative password reset are not included.
- Native device groups: list, create agent-based groups, edit name/description, delete owner/admin groups with typed full-ID confirmation. Membership and granular rights editing are not included.
- Account security and account actions are explicit browser shortcuts to the server website, not native operations or deep links to a particular dialog. Sign in and navigate to My account to use authenticator settings, security keys, backup codes, previous logins, notifications, time/language, password changes, own-account deletion and login tokens. Native implementation of these actions remains pending.
- Browser shortcuts open only a credential-free HTTPS URL. No cookie/token transfer, TLS bypass or automatic account changes. Web notifications are not Android push notifications.
- Profile owns its control socket. Generation checks discard stale callbacks; lifecycle pause closes the socket and clears the profile state. No automatic write retries. Exact response IDs and action names are required to associate acknowledgements. Only explicit result=ok is confirmed. Missing acknowledgements become unconfirmed after 20 seconds.
- Successful acknowledgements trigger list refresh, not independent state verification. Malformed, duplicate-ID or missing lists block changes until refresh. Server-side restrictions remain authoritative.
- Permissions are conservative: user management requires its site-admin bit; group edits require edit rights; deletion requires full owner/admin rights. Submission rechecks current targets and permissions. Password drafts are not persisted or logged.
- Four new permission-policy tests are included. Android compilation, unit tests, lint, device navigation and real-server mutation flows have NOT been executed in this environment.

This is an initial delivery, not the complete native account-management scope originally requested.
