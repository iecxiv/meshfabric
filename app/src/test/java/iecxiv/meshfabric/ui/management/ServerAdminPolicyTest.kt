package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerAdminPolicyTest {
    @Test fun requiresFullAdministratorInEitherRepresentation() {
        assertTrue(fullServerAdmin(-1L))
        assertTrue(fullServerAdmin(4294967295L))
        assertFalse(fullServerAdmin(0L))
        assertFalse(fullServerAdmin(21L))
    }
    @Test fun outputIsBoundedByCountAndCharacters() {
        var lines=emptyList<String>()
        repeat(300) { lines=boundedAdminOutput(lines,"x".repeat(2000)) }
        assertTrue(lines.size<=200)
        assertTrue(lines.sumOf { it.length }<=64000)
        assertTrue(boundedAdminOutput(emptyList(),"x".repeat(50000)).single().contains("truncated"))
    }
    @Test fun unknownExistingSourcesCanBePreservedButNotInvented() {
        assertTrue(validTraceSelection(setOf("custom"),setOf("custom","agent")))
        assertFalse(validTraceSelection(emptySet(),setOf("invented")))
        assertTrue(validTraceSelection(setOf("agent"),emptySet()))
    }
}
