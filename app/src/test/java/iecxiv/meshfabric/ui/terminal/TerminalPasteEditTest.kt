package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalPasteEditTest {
    private val original = TerminalPasteDraft("original", null)
    @Test fun editReplacesClipboardSnapshot() {
        assertEquals(TerminalPasteDraft("edited", null), editTerminalPaste(original, "edited"))
        assertEquals("original", original.text)
    }
    @Test fun preservesWhitespace() { assertEquals("  edited  ", editTerminalPaste(original, "  edited  ").text) }
    @Test fun canStartWithEmptyClipboard() { assertEquals(TerminalPasteDraft("new", null), editTerminalPaste(null, "new")) }
    @Test fun clearingDisablesSending() { assertEquals(TerminalPasteDraft("", TerminalPasteIssue.EMPTY), editTerminalPaste(original, "")) }
    @Test fun oversizeRejectsEntireEdit() {
        assertEquals(TerminalPasteDraft("original", TerminalPasteIssue.TOO_LARGE), editTerminalPaste(original, "a".repeat(16385)))
    }
    @Test fun controlInjectionRejected() {
        assertEquals(TerminalPasteDraft("original", TerminalPasteIssue.CONTROL), editTerminalPaste(original, 27.toChar().toString()))
    }
    @Test fun validEditClearsPreviousError() {
        assertNull(editTerminalPaste(TerminalPasteDraft("original", TerminalPasteIssue.CONTROL), "fixed").issue)
    }
    @Test fun multilineTracksEditedText() {
        val edited = editTerminalPaste(original, "one" + 10.toChar() + "two")
        assertNull(edited.issue)
        assertTrue(TerminalPastePolicy.multiline(edited.text!!))
        assertFalse(TerminalPastePolicy.multiline(editTerminalPaste(edited, "one two").text!!))
    }
    @Test fun invalidSurrogateRejected() {
        assertEquals(TerminalPasteIssue.UNICODE, editTerminalPaste(original, 0xD800.toChar().toString()).issue)
    }
}
