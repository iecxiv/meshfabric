package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralRunPolicyTest {
    private val node="node//a"
    private val meta=GeneralMeta("Equipo",emptyList(),131072,"Linux","mesh//a")
    private val inventory=listOf(MeshGroup(id=meta.mesh,name="Grupo",mtype=2) to listOf(MeshNode(id=node,name="Equipo",meshid=meta.mesh,conn=1)))
    private val draft=GeneralRunDraft("echo test","shell",meta.os)
    @Test fun targetsOnlyOneExactDeviceAndUsesServiceContext() {
        val p=generalRunPacket(node,"request",draft,meta,inventory)
        assertEquals("runcommands",p.getString("action"));assertEquals(1,p.getJSONArray("nodeids").length())
        assertEquals(node,p.getJSONArray("nodeids").getString(0));assertEquals(0,p.getInt("runAsUser"))
        assertTrue(p.getBoolean("reply"));assertEquals("request",p.getString("responseid"))
    }
    @Test fun linuxUsesShellTypeThree() {assertEquals(3,generalRunPacket(node,"request",draft,meta,inventory).getInt("type"))}
    @Test fun windowsPowerShellUsesTypeTwo() {
        val windows=meta.copy(os="Windows 11")
        assertEquals(2,generalRunPacket(node,"request",draft.copy(interpreter="powershell",os=windows.os),windows,inventory).getInt("type"))
    }
    @Test fun mismatchedInterpreterCannotBeSent() {assertTrue(runCatching {generalRunPacket(node,"request",draft.copy(interpreter="cmd"),meta,inventory)}.isFailure)}
    @Test fun changedOsInvalidatesFrozenReview() {assertTrue(runCatching {generalRunPacket(node,"request",draft,meta.copy(os="Windows"),inventory)}.isFailure)}
    @Test fun unknownSystemRequiresAnExplicitInterpreter() {
        val unknown=meta.copy(os="")
        assertTrue(runCatching {generalRunPacket(node,"request",draft.copy(interpreter="",os=""),unknown,inventory)}.isFailure)
        assertEquals(3,generalRunPacket(node,"request",draft.copy(os=""),unknown,inventory).getInt("type"))
    }
    @Test fun managementRightDoesNotReplaceRunCommandRight() {assertFalse(generalRunAvailable(node,meta.copy(rights=4),inventory))}
    @Test fun amtConnectivityIsNotAnAgentConnection() {
        val received=listOf(inventory.single().first to listOf(inventory.single().second.single().copy(conn=2)))
        assertFalse(generalRunAvailable(node,meta,received))
    }
    @Test fun rejectsDuplicateDeviceIdentity() {
        val received=listOf(inventory.single().first to inventory.single().second.let {it+it})
        assertFalse(generalRunAvailable(node,meta,received))
    }
    @Test fun deviceMustStillBelongToVerifiedGroup() {
        val received=listOf(inventory.single().first to listOf(inventory.single().second.single().copy(meshid="mesh//b")))
        assertFalse(generalRunAvailable(node,meta,received))
    }
    @Test fun nonAgentGroupCannotRun() {assertFalse(generalRunAvailable(node,meta,listOf(inventory.single().first.copy(mtype=3) to inventory.single().second)))}
    @Test fun invalidCommandTextIsBlocked() {
        assertFalse(generalRunTextValid(" "));assertFalse(generalRunTextValid("x".repeat(64001)));assertFalse(generalRunTextValid("bad\u0000text"))
        assertTrue(generalRunTextValid("echo uno\necho dos"))
    }
}
