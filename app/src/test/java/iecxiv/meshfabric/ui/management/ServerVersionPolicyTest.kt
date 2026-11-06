package iecxiv.meshfabric.ui.management

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ServerVersionPolicyTest {
    @Test fun updatePermissionIsSeparateFromBackupAndRestore() {
        assertTrue(serverUpdateAllowed(16))
        assertTrue(serverUpdateAllowed(-1))
        assertTrue(serverUpdateAllowed(4294967295))
        assertFalse(serverUpdateAllowed(1))
        assertFalse(serverUpdateAllowed(4))
    }
    @Test fun acceptsConcreteVersionsButNotAliasesOrCommandText() {
        listOf("1.2.9","1.3.0-beta.1","1.3.0+build.2").forEach { assertTrue(it,validServerVersion(it)) }
        listOf("latest","stable","1.2.3; reboot","1.2.3 --force","https://example.test/pkg"," ").forEach { assertFalse(it,validServerVersion(it)) }
    }
    @Test fun missingTagsStayMissing() {
        val tags=serverVersionTags("""{"action":"serverversion","tags":{"current":"1.2.9","latest":"1.3.0"}}""")!!
        assertEquals("1.2.9",tags.current)
        assertNull(tags.stable)
        assertEquals("1.3.0",tags.latest)
        assertNull(serverVersionTags("{}"))
    }
    @Test fun onlyAdvertisedConcreteTargetsCanBeRequested() {
        val tags=ServerVersionTags("1.2.9","1.2.9","1.3.0")
        assertFalse(mayUpdateServer(tags,"1.2.9"))
        assertFalse(mayUpdateServer(tags,"9.9.9"))
        assertFalse(mayUpdateServer(tags,"latest"))
        assertTrue(mayUpdateServer(tags,"1.3.0"))
        assertFalse(mayUpdateServer(ServerVersionTags(null,null,"1.3.0"),"1.3.0"))
    }
    @Test fun requestPinsExactVersionWithoutMovingTag() {
        val packet=JsonParser.parseString(serverUpdateRequest("1.3.0-beta.1")).asJsonObject
        assertEquals("serverupdate",packet.get("action").asString)
        assertEquals("1.3.0-beta.1",packet.get("version").asString)
        assertEquals(2,packet.size())
    }
}
