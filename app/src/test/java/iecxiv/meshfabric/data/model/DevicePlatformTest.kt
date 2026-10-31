package iecxiv.meshfabric.data.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DevicePlatformTest {
    @Test fun identifiesPlatformsWithoutDescription() {
        val expected = mapOf(3 to DevicePlatform.WINDOWS, 43 to DevicePlatform.WINDOWS,
            6 to DevicePlatform.LINUX, 32 to DevicePlatform.LINUX, 45 to DevicePlatform.LINUX,
            12 to DevicePlatform.ANDROID, 14 to DevicePlatform.ANDROID,
            11 to DevicePlatform.MACOS, 16 to DevicePlatform.MACOS, 29 to DevicePlatform.MACOS,
            10005 to DevicePlatform.MACOS, 17 to DevicePlatform.CHROMEOS,
            30 to DevicePlatform.FREEBSD, 37 to DevicePlatform.OPENBSD)
        expected.forEach { (id, platform) ->
            val node = MeshNode(agent=AgentInfo(id=id))
            assertEquals(platform,node.platform)
            assertEquals(platform.label,node.osDisplayName)
        }
    }
    @Test fun preservesDetailedDescription() {
        val node = MeshNode(agent=AgentInfo(id=4),osdesc="  Windows 11 Pro  ")
        assertEquals("Windows 11 Pro",node.osDisplayName)
    }
    @Test fun fallsBackForWhitespaceDescription() {
        assertEquals("Android",MeshNode(agent=AgentInfo(id=14),osdesc="  ").osDisplayName)
    }
    @Test fun parsesAgentFromServerJson() {
        val node = Gson().fromJson("{\"_id\":\"node//test\",\"agent\":{\"id\":14}}",MeshNode::class.java)
        assertEquals(DevicePlatform.ANDROID,node.platform)
        assertEquals("Android",node.osDisplayName)
    }
    @Test fun unknownDoesNotMeanLinux() {
        assertEquals(DevicePlatform.UNKNOWN,MeshNode(name="Windows PC",agent=AgentInfo(id=99999),icon=1).platform)
        assertEquals("",MeshNode().osDisplayName)
        assertEquals(DevicePlatform.UNKNOWN,MeshNode(agent=AgentInfo(id=23)).platform)
    }
    @Test fun usesDescriptionWhenAgentUnknown() {
        assertEquals(DevicePlatform.ANDROID,MeshNode(osdesc="Android 12 Linux").platform)
        assertEquals(DevicePlatform.LINUX,MeshNode(osdesc="Debian GNU/Linux 12").platform)
        assertEquals(DevicePlatform.WINDOWS,MeshNode(osdesc="Microsoft Windows 11").platform)
    }
    @Test fun knownAgentTypeTakesPriorityForPlatform() {
        assertEquals(DevicePlatform.ANDROID,MeshNode(agent=AgentInfo(id=14),osdesc="Linux").platform)
    }
}
