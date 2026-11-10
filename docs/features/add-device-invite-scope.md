# Agent installation scope

Agregar agente prepares installers, commands and mobile configuration for a received agent device group.

Invitar from a group and Invitar from a device are the same agent-installation invitation. The device entry point must resolve its received meshid in the current actor domain; nodeid is never an invitation target. A missing or ambiguous group disables the action.

Only createInviteLink and inviteAgent are invitation mutations in this feature. No temporary desktop, terminal or file access is created, listed or revoked.

This correction removes sharing helpers and outcomes introduced under the superseded interpretation. Historical audit extracts are retained unchanged. The replacement test cases cover the corrected scope; no failures are ignored and no baseline is introduced.

The corrected transport remains a candidate outside app. Dialogs and navigation are not yet implemented. Kotlin compilation and unit checks are not emulator execution or a real-server validation.
