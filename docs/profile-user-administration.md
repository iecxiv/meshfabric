# User and access administration — MeshFabric

[Español](es/profile-user-administration.md) | [Management](profile.md)

## Users

User administration requires server-granted authority and stays within the current domain. Creation follows the identity mode announced by the server: username and email are separate when userNameIsEmail is false; email is the login identifier when true. An existing username account does not determine the mode for newly created accounts. The current creation form requires a valid email and matching repeated passwords in both modes.

Edit email and real name; edit phone or administrative email-verification status only when the server conditions permit. Marking an email verified is an administrative state change, not proof of mailbox ownership or a verification-message send.

Advanced controls cover supported server rights, restrictions, features, consent, quota and administrative realms. Unknown/protected rights and full-administrator masks remain guarded. Administrative realms are not user-group memberships. Lock/unlock preserves other rights. User deletion requires the exact account name, review and separate confirmation.

For your own account, messaging, lock/unlock and administrative deletion are hidden. Two separate password and MFA actions lead to native My Account controls. Other users retain their existing administrative controls, including password change preserving MFA, explicit MFA reset and reviewed individual notification. Visibility does not replace internal permission checks.

## Groups and access

- Create, rename, describe and delete supported user groups; external-group restrictions remain enforced.
- Add or remove one eligible member at a time. Member addition retains the conservative identifier and known-link checks.
- Create, review, edit and remove supported assignments between user groups and device groups.
- Grant access to existing visible devices; this does not install an agent.
- Grant, review, edit and remove direct user access to device groups without treating it as inherited access.
- Device-group advanced settings include supported flags, mandatory consent and inactivity expiry from 0 to 2000 days. Zero removes a group-specific expiry override; it does not guarantee that global automatic removal is disabled.

Permissions and restrictions are distinct. Initial grants do not invent dependencies or unknown bits. Existing-rights edits preserve protected and unknown bits and reject unsupported full-administrator edits. Removing one membership or assignment does not prove the user lost every effective access path.

## Messages, results and validation

Broadcast and individual notifications have different scopes and reviewed confirmation. They do not provide guaranteed delivery/read receipts or automatic retries.

Received names and context are preferred over visible internal IDs. Ambiguous or incomplete targets cannot authorize sensitive operations. Correlated replies and required readbacks distinguish acknowledged/observed results from unconfirmed outcomes. Concurrent changes can still occur between preflight reads and submission.

Unit and Android/Compose regressions cover policies, UI state, identity changes, navigation and readbacks. Compiling tests is not executing them; passing automation does not validate every live-server mutation or every physical-device layout.
