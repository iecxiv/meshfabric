# Agent installation and invitations

[Español](es/agent-installation.md) | [Management](profile.md) | [README](../README.md)

## Entry points

Open Management, Device Groups and an agent-based group. Add agent prepares installation material; Invite generates an installation link for that group. Invite is not an action in the three-dot menu of device cards in Devices. Neither operation provides temporary access to an existing device or email invitations. Server permissions remain authoritative.

## Add agent

Select the platform and available architecture or mode. Options cover Windows installers, Linux/BSD commands and binary architectures, macOS installers, MeshCentral Assistant and Android MeshAgent APK/QR configuration. Availability depends on received metadata and the server. Missing configuration is not invented.

Downloads use the current session against the configured origin, without following redirects, under the configured certificate policy and a 128 MiB bound. A private temporary file is prepared before opening Android's destination picker. The save flow counts the copied bytes and rereads the destination to compare size and SHA-256. A zero-byte file, failed verification or changed identity is not reported as a successful save. Readback verifies copy consistency, not publisher identity or an installer's signature. Cancellation and provider errors can prevent a save; deletion of an incomplete destination is best effort.

The installation dialog is owned outside the group-details content so that removal of that content does not itself destroy the pending save. This does not guarantee recovery after process death.

Linux/BSD install and uninstall commands retain the direct-execution alternative after sudo. Both invocations receive the same arguments; uninstall includes its argument in both. Shell AND/OR ordering means the fallback can also run after an earlier failure, not only when sudo is unavailable. Review commands before using them on a test host. MeshFabric never executes them or installers locally.

## Invite

Choose expiration, agents and installation type, then press Generate link. Opening the dialog or changing a selector does not create a link.

- Expiration: 1 hour, 8 hours, 1 day, 1 week, 1 month or Unlimited. One month means 30 days.
- Agents: all available, Windows, Linux, macOS, MeshCentral Assistant or Android MeshAgent.
- Installation type: background and interactive, background only or interactive only. Assistant and Android hide this option and reset its value; returning does not restore a hidden selection.

The independent control channel sends authentication arguments before requesting user information. The expected identity must be validated before the group-link request. Replies are correlated with the request and group. There are no automatic mutation retries.

Copy the generated link only after success is confirmed. Options, identity, connection or authority changes and leaving the foreground invalidate the locally displayed result. This does not revoke a link already generated on the server. Treat invitation links and QR configuration as sensitive.

## Errors and verification

Diagnostics distinguish connection, handshake send, identity wait and link-reply wait. A send accepted locally is not proof of server processing. An unknown result is not success; inspect the state before retrying. Share only the error text/code, never cookies, complete invitation links or QR configuration.

Automated test results, visual review, successful download, successful installation and enrollment into the correct group are separate checks. A published workflow or APK does not establish all of them. Use the [acceptance checklist](features/add-device-invite-acceptance.md) to record actual observations.
