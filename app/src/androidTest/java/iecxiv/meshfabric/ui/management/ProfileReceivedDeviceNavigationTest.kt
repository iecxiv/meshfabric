package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.model.MeshNode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileReceivedDeviceNavigationTest {
    private val id="node//one"
    private val mid="mesh//group"
    private fun node()=MeshNode(id=id,name="Device",meshid=mid)
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin"),nodes=listOf(JSONObject().put("_id",id).put("meshid",mid)))
    @Test fun nativeNodeMustExistInBothReceivedSets() {val actual=node();assertSame(actual,profileReceivedDevice(state(),listOf(actual),id,true));assertNull(profileReceivedDevice(state(),emptyList(),id,true));assertNull(profileReceivedDevice(state().copy(nodes=null),listOf(actual),id,true))}
    @Test fun ambiguityAndMeshMismatchAreRejected() {assertNull(profileReceivedDevice(state(),listOf(node(),node()),id,true));assertNull(profileReceivedDevice(state(),listOf(node().copy(meshid="mesh//other")),id,true))}
    @Test fun invalidBindingAndUnreadyDataDoNotOpenInventory() {assertNull(profileReceivedDevice(state(),listOf(node()),id,false));assertNull(profileReceivedDevice(state().copy(busy=true),listOf(node()),id,true));assertNull(profileReceivedDevice(state().copy(readError=true),listOf(node()),id,true));assertNull(profileReceivedDevice(state().copy(detailsBusy=true),listOf(node()),id,true))}
}
