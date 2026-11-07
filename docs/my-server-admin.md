# My Server: Console and Trace

This delivery adds native server-console and server-wide tracing controls. Backup and restore remain unimplemented.

A separate control socket exists only while Console or Trace is composed and the host lifecycle is RESUMED. It uses the existing session and TLS preference. It never resends commands or trace mutations after failure. Opening a page sends authentication and userinfo only, not administrative commands. General and Statistics retain their existing read-only channel.

Console requires full site administrator rights (-1 or 4294967295). The exact command is reviewed with the server URL before sending serverconsole/value. A reply is displayed, never interpreted as unconditional success. On timeout or disconnection during a pending operation, the result is explicitly unknown and that socket is retired to prevent stale responses being attributed to a new command.

Trace receives initial traceinfo and event/traceinfo, plus trace/source/args/time messages. Applying traceinfo/traceSources replaces SERVER-WIDE sources and requires a separate explicit confirmation. Existing unrecognized source names are preserved unless deliberately deselected. A changed source set invalidates the draft; the channel checks the reviewed base set again before sending. No command to clear sources is sent on close. MeshCentral may clear global sources itself when its last full-admin session disconnects.

The server remains authoritative for permissions and domain-level myserver restrictions. A full-admin UI check does not override server configuration. Server/credential identity is checked again before writes. There are no embedded credentials, additional persistence, or diagnostic logging of commands, replies, trace lines or session cookies.

Output is in-memory only, limited to 200 entries and 64000 characters per buffer; entries over 16000 characters are visibly truncated. Buffers are cleared when the channel closes or backgrounds. Clear output affects only local text, never server logs or trace sources. Trace text may contain sensitive data.

Unit tests cover full-admin bitmask handling, bounded buffers, and preservation of unknown current sources. They were added but not run in the editing environment. Build, lint, integration behavior and visual layout still require CI/device verification.

Protocol reference consulted: d-maggipinto/meshcentral-desktop docs/PROTOCOL.md, My Server section. Runtime compatibility against the user's server has not been verified.
