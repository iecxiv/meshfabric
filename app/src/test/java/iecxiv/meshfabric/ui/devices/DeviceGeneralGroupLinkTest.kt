package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralGroupLinkTest {
    private val actor="user//alice"
    private val group=MeshGroup(id="mesh//a",name="Mismo nombre",desc="Grupo A")
    private val node=MeshNode(id="node//a",name="Equipo A",meshid=group.id)
    private val inventory=listOf(group to listOf(node))
    @Test fun resolvesByIdInsteadOfName() {
        val other=MeshGroup(id="mesh//b",name=group.name,desc="Grupo B")
        val target=generalGroupTarget(actor,other.id,inventory+(other to emptyList()),true,false)
        assertEquals(other.id,target?.group?.id)
        assertEquals("Grupo B",target?.group?.desc)
    }
    @Test fun rejectsDuplicateGroupIds() {assertNull(generalGroupTarget(actor,group.id,inventory+inventory,true,false))}
    @Test fun rejectsUnknownGroupRatherThanMatchingName() {assertNull(generalGroupTarget(actor,"mesh//unknown",inventory,true,false))}
    @Test fun rejectsCrossDomainGroup() {assertNull(generalGroupTarget("user/other/alice",group.id,inventory,true,false))}
    @Test fun disconnectedOrLoggedOutCannotOpen() {
        assertNull(generalGroupTarget(actor,group.id,inventory,false,false))
        assertNull(generalGroupTarget(actor,group.id,inventory,true,true))
    }
    @Test fun includesOnlyUnambiguousDevicesInTheExactGroup() {
        val foreign=node.copy(id="node//b",meshid="mesh//b")
        val duplicate=node.copy(id="node//duplicate")
        val result=generalGroupTarget(actor,group.id,listOf(group to listOf(node,foreign,duplicate,duplicate)),true,false)
        assertEquals(listOf(node),result?.nodes)
    }
    @Test fun invalidIdentifiersCannotOpen() {
        assertNull(generalGroupTarget("alice",group.id,inventory,true,false))
        assertNull(generalGroupTarget(actor,"mesh//",inventory,true,false))
    }
}
