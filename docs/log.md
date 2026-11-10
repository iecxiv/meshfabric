# Registro / Log

The former Activity destination is labelled Registro in Spanish and Log in English. It contains Activity and Events tabs using the Management/Server tab component. Resource files are named log.xml; resource identifiers remain compatible.

Activity retains its journal, filters and command feedback. Search and filter state are saveable across switching between the two tabs.

Events performs a read-only query on a separate authenticated control channel, after checking the exact actor. Requests use action=events, a unique tag and a fixed limit of 200. Only matching responses are accepted. No event deletion, unbounded query, automatic retry or claim of complete server audit coverage is provided. Search filters the received records locally. Reopening Events or resuming the screen requests a fresh bounded snapshot; Refresh is manual while the screen remains active.

Timeout, malformed data, explicit denial and empty returned history are distinct states. Channel failure, lifecycle pause, observed access changes and account/connection changes clear the snapshot. Server-side authorization remains authoritative, and there is no transactional guarantee against concurrent access changes. Live events are not appended to the historical snapshot and do not confirm remote action execution.

Validation: execute JVM tests, lint, debug and Android-test compilation, then the scoped RegistroSectionTabsTest, LogScreenTest and ManagementNavigationLabelTest classes. Run the complete suite before final integration into dev. Automated Compose checks do not replace a physical-device visual review or live-server compatibility verification.

Decode diagnostics distinguish frame size, JSON/envelope, account identity, history shape, field type/length and internal processing failures. The technical detail contains only fixed enum codes, JSON type and optional length/count, never payload, account, token or event content. Limits remain 200 records, 4096 characters per text field and 2000000 characters per frame. Invalid batches are not silently truncated or partially accepted. Exact live-server incompatibility remains unverified until the new technical detail is observed.
