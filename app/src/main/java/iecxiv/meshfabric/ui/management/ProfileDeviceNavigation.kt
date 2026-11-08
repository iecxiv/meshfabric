package iecxiv.meshfabric.ui.management

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.ui.devices.DeviceDetailsDialog
import iecxiv.meshfabric.ui.devices.LocalDeviceDetailsClient

internal fun profileDeviceIdsMatch(actor:String,id:String,profileMesh:String,visibleMesh:String)=actor.startsWith("user/") && actor.split('/').size==3 && id.startsWith("node/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && id.length<=134 && !id.contains('\u0000') && userDomain(actor)==userDomain(id) && profileGroupMeshId(actor,profileMesh) && profileMesh==visibleMesh
internal fun profileReceivedDevice(s:ProfileState,visible:List<MeshNode>,id:String,bound:Boolean):MeshNode? {
    if(!bound || !s.ready || s.busy || s.loading || s.detailsBusy || s.readError) return null
    val received=s.nodes?.singleOrNull {it.optString("_id")==id} ?: return null
    val node=visible.singleOrNull {it.id==id} ?: return null
    return node.takeIf {profileDeviceIdsMatch(s.id,id,received.optString("meshid"),it.meshid)}
}
@Composable internal fun ProfileDeviceDetailsOverlay(source:MeshCentralClient,session:ProfileSession,s:ProfileState,visible:List<MeshNode>,id:String,onDismiss:()->Unit) {
    val node=profileReceivedDevice(s,visible,id,session.broadcastServerLabel().isNotBlank())
    if(node!=null) CompositionLocalProvider(LocalDeviceDetailsClient provides source) {DeviceDetailsDialog(node,onDismiss)} else {
        val es=LocalConfiguration.current.locales[0].language=="es"
        ManagementDialog(onDismissRequest=onDismiss,properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),title={Text(if(es) "Dispositivo no disponible" else "Device unavailable")},text={Text(if(es) "No se puede abrir esta ficha con los datos y conexión actuales. No se reconstruye un dispositivo a partir de datos parciales y su ausencia no acredita que se haya eliminado." else "This entry cannot be opened with current data and connection. A device is not reconstructed from partial data and absence does not prove deletion.")},confirmButton={TextButton(onClick=onDismiss) {Text(if(es) "Volver" else "Back")}},dismissButton={})
    }
}

internal fun profileFlattenReceivedDevices(groups:List<Pair<MeshGroup,List<MeshNode>>>):List<MeshNode> = groups.flatMap {it.second}
