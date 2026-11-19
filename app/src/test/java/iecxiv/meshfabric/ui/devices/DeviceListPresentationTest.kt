package iecxiv.meshfabric.ui.devices

import com.google.gson.Gson
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.*
import org.junit.Assert.*
import org.junit.Test

class DeviceListPresentationTest {
    private val group=MeshGroup(id="mesh//one",name="Group")
    private fun node(id:String,tags:List<String> = emptyList(),power:Int?=null)=MeshNode(id=id,name=id,meshid=group.id,tags=tags,pwr=power)
    private fun sections(nodes:List<MeshNode>,sort:DeviceListSort)=deviceListSections(listOf(group to nodes),sort,false,false,true)
    @Test fun flatCriteriaNeverCreateSeveralSections() {
        for(sort in listOf(DeviceListSort.DEVICE,DeviceListSort.LAST_SEEN,DeviceListSort.LAST_BOOT)) {
            assertFalse(deviceListUsesSections(sort));assertEquals(1,sections(listOf(node("b"),node("a")),sort).size)
        }
        assertEquals(listOf("a","b"),sections(listOf(node("b"),node("a")),DeviceListSort.DEVICE).single().second.map {it.id})
    }
    @Test fun tagsDuplicateAppearancesButSelectionUsesUniqueIds() {
        val result=sections(listOf(node("a",listOf("x","y")),node("b",listOf("x"))),DeviceListSort.TAGS)
        assertEquals(3,result.sumOf {it.second.size})
        assertEquals(setOf("a","b"),DeviceListSelection("scope").selectVisible("scope",result.flatMap {it.second}.map {it.id}.toSet()).ids)
        val keys=result.flatMap {(g,ns)->ns.map {g.id to it.id}}
        assertEquals(keys.size,keys.distinct().size)
    }
    @Test fun groupTagsRetainUntaggedAndSeparateSameNameGroups() {
        val result=sections(listOf(node("a"),node("b",listOf("tag"))),DeviceListSort.GROUP_TAGS)
        assertEquals(setOf("Group","Group - tag"),result.map {it.first.name}.toSet())
        val other=group.copy(id="mesh//other")
        val sameNames=deviceListSections(listOf(group to listOf(node("a")),other to listOf(node("b").copy(meshid=other.id))),DeviceListSort.GROUP_TAGS,false,false,true)
        assertEquals(2,sameNames.map {it.first.id}.distinct().size)
    }
    @Test fun offSectionRequiresExplicitReportedOffAndNonemptyRows() {
        assertEquals(2,sections(listOf(node("on",power=1),node("unknown")),DeviceListSort.POWER).size)
        assertEquals(setOf("Encendido","Apagado","Desconocido"),sections(listOf(node("on",power=1),node("off",power=8),node("unknown")),DeviceListSort.POWER).map {it.first.name}.toSet())
        for(value in listOf(null,0,2,3,4,5,7,9)) assertEquals(0,deviceListPowerSection(value))
        assertEquals(2,deviceListPowerSection(6))
        assertEquals(2,deviceListPowerSection(8))
    }
    @Test fun connectivityNeverInventsPower() {
        assertEquals("Desconocido",sections(listOf(node("a").copy(conn=1)),DeviceListSort.POWER).single().first.name)
    }
    @Test fun offlineTemporarilyOverridesSavedVisibilityWithoutChangingIt() {
        for(saved in listOf(false,true)) {
            assertTrue(deviceListEffectiveShowDisconnected(saved,DeviceListFilter.OFFLINE))
            assertEquals(saved,deviceListEffectiveShowDisconnected(saved,DeviceListFilter.ALL))
            val groups=listOf(group to listOf(node("on").copy(conn=1),node("off")))
            val filtered=deviceListFiltered(groups,DeviceListFilter.OFFLINE,saved,"",false,emptySet(),emptySet())
            assertEquals(listOf("off"),filtered.single().second.map {it.id})
        }
    }
    @Test fun realNameIsNotOperatingSystemDescription() {
        val n=node("alias").copy(rname="HOST",osdesc="Linux")
        assertEquals("HOST",deviceListDisplayName(n,true));assertEquals("alias",deviceListDisplayName(n,false))
        assertEquals("alias",deviceListDisplayName(n.copy(rname=null),true))
    }
    @Test fun metadataSurvivesInventoryDecodingAndCopies() {
        val n=Gson().fromJson("""{"_id":"a","rname":"HOST","intelamt":{},"sessions":{"help":1,"kvm":{"user":1}}}""",MeshNode::class.java).copy(conn=1)
        assertEquals("HOST",n.rname)
        for(filter in listOf(DeviceListFilter.AMT,DeviceListFilter.HELP,DeviceListFilter.SESSIONS)) assertTrue(deviceListMatchesFilter(n,filter,emptySet(),emptySet()))
    }
    @Test fun missingMetadataDoesNotInventFilters() {
        for(filter in listOf(DeviceListFilter.AMT,DeviceListFilter.HELP,DeviceListFilter.SESSIONS,DeviceListFilter.STARRED)) assertFalse(deviceListMatchesFilter(node("a"),filter,emptySet(),emptySet()))
        assertTrue(deviceListMatchesFilter(node("a"),DeviceListFilter.STARRED,setOf("a"),emptySet()))
        assertTrue(deviceListMatchesFilter(node("node//a"),DeviceListFilter.SESSIONS,emptySet(),setOf("a")))
        assertFalse(deviceListMatchesFilter(node("a").copy(sessions=JsonParser.parseString("{\"kvm\":0,\"help\":false}")),DeviceListFilter.SESSIONS,emptySet(),emptySet()))
    }
}
