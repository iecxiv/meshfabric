# Windows drive navigation

Windows nodes identified by their OS description request the virtual drive selector with ls path "". The UI keeps "/" as the logical selector location; Linux continues to request "/" unchanged. Only drive entries actually returned by the agent are shown.

Drive replies use t=1 and n/name, with optional dt/s/f metadata. Normal and recovery-core variants and dir/files/list aliases are accepted. Drive paths are absolute, normalized for Windows separators, and never prefixed with the virtual root. Units, removable disks and optical drives have navigation-only rows; unknown capacities are omitted. Up from a drive, a cached volume mount point or a UNC share returns to the selector. The Drives header button returns there directly.

The existing navigation request IDs, socket epochs, queued requests and reconnect behavior remain in use. Old root replies cannot overwrite a newer folder choice. Creation, upload and paste at the selector and mutation actions on drive roots are blocked. Ordinary folder operations and background transfer implementations are unchanged.

JVM tests cover request paths, modern/recovery metadata, unknown sizes, unsafe/duplicate entries, mounted volumes, UNC roots, Linux paths and stale navigation replies. Device validation is still required for real Windows agents, drive changes, permissions, disconnected or mapped network drives, reconnection and transfers inside drive roots. Nodes without a Windows OS description are not assumed to be Windows. Inventory volumes are not used to invent navigable drives.
