# Terminal archives and the save-buffers setting

English | [Español](es/terminal-archive-persistence.md)

Closing with X (or closing all sessions) retires the current transport and archives its local record. Archives are separate from the current-session registry: they do not highlight the device button, reconnect automatically, or indicate that a remote process survived. Opening terminal starts a new record; previous records are selected through History.

With Save terminal buffers enabled, archives and checkpoints use app-private no-backup storage, separated by a hash of server URL and username. Output is plain text, not encrypted. Stable record IDs prevent duplicate archives. Writes use a synchronized temporary file, fsync, and replacement; malformed or oversized stored records are not loaded.

With the setting disabled, records remain in memory only. Disabling it removes saved archive files and legacy buffer files. It is checked again on writes, including delayed checkpoints; the setting is preserved across logout. Enabling it allows subsequent checkpoints and closes to save again.

Output checkpoints are throttled to approximately one second. The activity also checkpoints onStop. Removing the task cancels pending terminal reconnects and archives all current terminal records through the existing service callback. These callbacks cannot guarantee recovery of output never written before abrupt process termination; restart restores the last complete saved record as read-only history, never as a current disconnected session.

Deleting archives also deletes their saved files. Only records reviewed by the deletion action are removed; current sessions are excluded. Legacy per-device buffers are migrated only when a remembered account identifies their owner at startup. Unidentified legacy files are not assigned to a newly entered account.

Validation: disk-store JVM tests cover upsert/deduplication, account separation, disabled persistence, durable deletion, path containment, and oversized record rejection. Device validation must still cover X, recent-task removal, restart, the setting in both states, account changes, and deletion followed by restart.
