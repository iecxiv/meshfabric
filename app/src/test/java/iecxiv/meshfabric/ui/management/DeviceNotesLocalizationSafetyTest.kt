package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class DeviceNotesLocalizationSafetyTest {
    @Test fun receivedContentIsPreservedVerbatim() {
        val content="Notas / Notes\n  ñ & <texto>\n"
        val notes=DeviceNotes("node//one","Equipo").received("node//one",content)
        assertEquals(content,notes.original)
        assertEquals(content,notes.draft)
        assertEquals(NotesPhase.READY,notes.phase)
        assertFalse(notes.dirty)
    }
    @Test fun confirmationStillRequiresAnExactReread() {
        val pending=DeviceNotes("node//one","Equipo",editable=true,phase=NotesPhase.READY,original="Original",draft="Borrador ñ",loaded=true).saving()
        assertEquals(pending,pending.received("node//other","Borrador ñ"))
        assertEquals(pending,pending.received("node//one","Otro contenido"))
        val confirmed=pending.received("node//one","Borrador ñ")
        assertEquals(NotesPhase.READY,confirmed.phase)
        assertEquals("Borrador ñ",confirmed.original)
        assertEquals("Borrador ñ",confirmed.draft)
        assertTrue(confirmed.message.isNotBlank())
        assertFalse(confirmed.dirty)
    }
    @Test fun failureKeepsTheDraftAndOriginalError() {
        val notes=DeviceNotes("node//one","Equipo",editable=true,phase=NotesPhase.READY,original="Original",draft="Borrador",loaded=true)
        val failed=notes.saving().failed("Remote detail: ñ <raw>")
        assertEquals(notes.draft,failed.draft)
        assertEquals(notes.original,failed.original)
        assertEquals("Remote detail: ñ <raw>",failed.error)
        assertEquals(NotesPhase.READY,failed.phase)
        assertTrue(failed.dirty)
    }
}
