# Native server restore

This delivery adds a restore UI, not a tested or completed restore of any live server. No destructive integration test was performed.

The user selects a document. MeshFabric checks restore site right 4 separately from backup right 1, requests an authcookie, queries the system-password availability, and freezes the selected ZIP into private cache. The file is bounded to 256 MiB and SHA-256 is displayed in the final review. The original document is never modified or deleted.

Zip4j 2.11.6 reads archive metadata and, where the password is known, streams entries without extraction. The UI accepts only archives containing config.json and meshcentral.db, intended for MeshCentral's NeDB web restore, not MongoDB/SQL. Paths with parent traversal, absolute paths and symlink entries are rejected; declared and actual expansion are bounded to 1 GiB and entry count to 20000. These checks do not prove that the database/configuration is semantically correct, compatible, or safe to restore.

With a custom password the entries are read before review. With the system password the app does not know the key and skips encrypted entry reads; a second explicit checkbox acknowledges that those contents and the key were not verified locally. Do not call this full archive validation in system-password mode.

The final confirmation identifies server URL, filename, size and hash, warns of data replacement/restart, and requires the exact text RESTORE. It is followed by a fresh permission/authcookie check. The password is sent over the control channel and a subsequent check response serves only as an ordering barrier, not proof of the actual password value. The server's temporary password setting is global: other administrator sessions can still race with it. The UI warns against concurrent operations and prevents starting backup/restore concurrently from this view.

Restore requires HTTPS (and reuses the existing certificate-validation preference). HTTP upload preserves domain paths/query parameters, uses datafile and auth multipart fields plus the existing web Cookie, and disables HTTP redirects and connection retries. A non-replayable file body is used. HTTP 2xx means request accepted only; it does not verify the database restoration, password acceptance, successful restart, or subsequent login. Interruption after starting the request is explicitly unknown, with no automatic retry. The live server's actual restore authorization/response behavior still needs validation in a disposable test deployment.

Private cache snapshots and in-memory passwords are not added to diagnostic logs or saved UI state. Temporary files are deleted on normal completion/cancellation. An OS kill can leave a private cache file until Android clears cache; secure deletion is not guaranteed. Backgrounding interrupts preparation/upload; file-picker backgrounding is exempt. Closing/cancelling upload cannot undo server processing already started. Buffers and confirmations are not persisted across process death.

Existing download-backup validation remains header-only; adding Zip4j here does not upgrade that earlier path. The server version remains 1.2.9. Tests were added, not executed in the editing environment; CI, device layout and a disposable-server integration test remain required.
