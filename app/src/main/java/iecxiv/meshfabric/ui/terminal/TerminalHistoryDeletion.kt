package iecxiv.meshfabric.ui.terminal

/** Resolve only the IDs reviewed in the confirmation; never include the current record. */
internal fun terminalHistoryDeletionIds(requested: Set<String>, archived: Set<String>, current: String): Set<String> =
    requested.intersect(archived) - current
