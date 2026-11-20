package iecxiv.meshfabric.ui.devices
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.deviceInventory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
internal fun agentCoreInventoryValue(state:DeviceInventoryState):String? = inventorySections(state).filter {it.key=="agent"}.flatMap {it.rows}.filter {it.path.split(" / ").last().equals("core",true)}.map {it.value}.distinct().singleOrNull()
internal suspend fun readAgentCoreInventoryVersion(source:MeshCentralClient,nodeId:String):String? = withTimeoutOrNull(26_000L) {
    agentCoreInventoryValue(deviceInventory(source,nodeId).first {"getsysinfo" !in it.pending})
}
