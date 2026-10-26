package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test
class DeviceNotesTest {
    private fun loaded()=DeviceNotes("node/tenant/a","Equipo",true).received("node/tenant/a","Anterior")
    @Test fun loadsExistingContent() { val s=loaded(); assertTrue(s.loaded); assertEquals("Anterior",s.draft); assertFalse(s.dirty) }
    @Test fun ignoresOtherDevice() { val s=DeviceNotes("node/tenant/a","Equipo"); assertEquals(s,s.received("node/tenant/b","Ajeno")) }
    @Test fun emptyTextIsAnExplicitEdit() { val s=loaded().edit(""); assertTrue(s.canSave); assertEquals("",s.draft) }
    @Test fun readOnlyCannotChangeOrSave() { val s=DeviceNotes("node/tenant/a","Equipo").received("node/tenant/a","Texto"); assertEquals(s,s.edit("Otro")); assertFalse(s.canSave) }
    @Test fun mismatchDoesNotConfirmSave() { val s=loaded().edit("Nuevo").saving().received("node/tenant/a","Anterior"); assertEquals(NotesPhase.SAVING,s.phase); assertTrue(s.dirty) }
    @Test fun matchingReadbackConfirmsSave() { val s=loaded().edit("Nuevo").saving().received("node/tenant/a","Nuevo"); assertEquals(NotesPhase.READY,s.phase); assertFalse(s.dirty); assertTrue(s.message.isNotBlank()) }
    @Test fun saveFailurePreservesDraft() { val s=loaded().edit("Nuevo").saving().failed("Sin confirmación"); assertEquals("Nuevo",s.draft); assertEquals("Anterior",s.original); assertTrue(s.canSave) }
    @Test fun loadFailureDoesNotEnableSaving() { val s=DeviceNotes("node/tenant/a","Equipo",true).failed("Sin permisos"); assertEquals(NotesPhase.ERROR,s.phase); assertFalse(s.canSave) }
}
