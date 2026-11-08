package iecxiv.meshfabric.ui.management

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class ProfileDeviceGroupedDataTest {
    @Test fun emptyGroupedListHasNoNodes() {assertTrue(profileFlattenReceivedDevices(emptyList()).isEmpty())}
    @Test fun actualPairShapePreservesOrderAndNodeIdentity() {val a=MeshNode(id="node//one",meshid="mesh//one");val b=MeshNode(id="node//two",meshid="mesh//two");val groups=listOf(MeshGroup(id="mesh//one") to listOf(a),MeshGroup(id="mesh//empty") to emptyList(),MeshGroup(id="mesh//two") to listOf(b));val result=profileFlattenReceivedDevices(groups);assertEquals(listOf(a,b),result);assertSame(a,result[0]);assertSame(b,result[1])}
    @Test fun duplicatesAreRetainedForTheAmbiguityGuard() {val node=MeshNode(id="node//one",meshid="mesh//one");val result=profileFlattenReceivedDevices(listOf(MeshGroup(id="mesh//one") to listOf(node,node)));assertEquals(2,result.size)}
}
