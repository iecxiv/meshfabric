package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeyLayoutTest {
    @Test fun missingPreferenceUsesDefaultOrder() {
        assertEquals(terminalDefaultKeyIds, parseTerminalKeyIds(null))
        assertTrue(terminalDefaultKeyIds.containsAll(listOf("ctrl-z", "ctrl-x", "ctrl-o", "ctrl-w")))
    }
    @Test fun emptyPreferenceMeansUserRemovedEveryKey() {
        assertEquals(emptyList<String>(), parseTerminalKeyIds(""))
    }
    @Test fun unknownAndDuplicateIdsAreDropped() {
        assertEquals(listOf("tab", "esc"), parseTerminalKeyIds("tab,tab,bogus, esc ,"))
    }
    @Test fun serializationRoundTripKeepsOrder() {
        val ids = listOf("ctrl-a", "enter", "esc")
        assertEquals(ids, parseTerminalKeyIds(serializeTerminalKeyIds(ids)))
    }
    @Test fun catalogHasNoDuplicatesAndIncludesEveryDefault() {
        assertEquals(terminalCatalogKeyIds.size, terminalCatalogKeyIds.toSet().size)
        assertTrue(terminalCatalogKeyIds.containsAll(terminalDefaultKeyIds))
        assertEquals(40, terminalCatalogKeyIds.size)
    }
}
