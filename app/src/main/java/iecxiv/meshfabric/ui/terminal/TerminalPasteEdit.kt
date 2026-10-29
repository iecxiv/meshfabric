package iecxiv.meshfabric.ui.terminal

// Invalid edits are rejected atomically; never silently truncate or send them.
internal fun editTerminalPaste(previous: TerminalPasteDraft?, value: String): TerminalPasteDraft {
    val issue = TerminalPastePolicy.issue(value)
    return if (issue == null || issue == TerminalPasteIssue.EMPTY) TerminalPasteDraft(value, issue)
    else TerminalPasteDraft(previous?.text.orEmpty(), issue)
}
