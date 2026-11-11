# Agent installation scope

## Implemented entry points

Management agent-group actions expose Add agent and Invite. Invite is not present in the three-dot menu of device cards in Devices. Installation invitations target the selected received group, never a node identifier or a guessed group. Missing or unauthorized records block the operation.

Add agent provides supported installers, architecture and mode choices, Linux/BSD install/uninstall commands, Assistant options and Android APK/QR configuration. Invite creates an agent-installation link using createInviteLink; it is not email delivery or temporary device/session sharing. Historical upstream audit evidence is not changed by this documentation update.

## Options and lifecycle

Expiration choices are 1 hour, 8 hours, 1 day, 1 week, 1 month (30 days) and Unlimited. Agent choices are all available, Windows, Linux, macOS, Assistant and Android. Installation modes are background and interactive, background only and interactive only. Assistant and Android hide and reset the mode. Generation requires an explicit button press.

The channel authenticates before requesting identity and validates that identity before the mutation. Replies must match request and group. Unknown outcomes do not trigger automatic resends. Context changes and leaving the foreground invalidate the local link, not a link previously issued by the server.

Agent dialogs are owned outside the group-details content. Downloads use authenticated, origin-bound requests without redirects, a 128 MiB limit and private temporary files. Saving requires byte-count and SHA-256 readback consistency. This is not installer signature verification. Incomplete-destination cleanup is best effort. Linux/BSD commands preserve the direct fallback for both installation and uninstall; no command or installer is executed by the app.

## Verification boundaries

Automated reports, APK publication, visual checks, actual downloads and correct-group enrollment are distinct evidence. Do not infer live-server acceptance from CI success or a workflow commit. Record the run, source commit, artifact and observed results in the acceptance checklist. No merge into dev or main is authorized by a test result.

User guide: [Agent installation and invitations](../agent-installation.md). Spanish guide: [Instalación de agentes e invitaciones](../es/agent-installation.md). Acceptance: [Checklist](add-device-invite-acceptance.md).
