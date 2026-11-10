package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
internal data class EventDevice(val nodeId:String="",val name:String="",val meshId:String="",val group:String="")
private fun JsonObject.label(key:String)=get(key)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString.orEmpty()
private fun eventKey(id:String,prefix:String):String = when {
    id.isBlank()->""
    id.startsWith("$prefix/")->id
    !id.contains('/')->"$prefix//$id"
    else->""
}
internal fun serverEventDevice(item:JsonObject):EventDevice {
    val action=item.label("action")
    val raw=item.label("nodeid")
    val nested=item.get("node")?.takeIf {it.isJsonObject}?.asJsonObject?.takeIf {raw.isBlank() || (eventKey(raw,"node").isNotBlank() && eventKey(it.label("_id"),"node")==eventKey(raw,"node"))}
    val args=item.get("msgArgs")?.takeIf {it.isJsonArray}?.asJsonArray
    fun arg(index:Int)=args?.takeIf {it.size()>index}?.get(index)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString.orEmpty()
    val name=item.label("nodename").ifBlank {nested?.label("name").orEmpty()}.ifBlank {if(action=="removenode") arg(0) else ""}
    val mesh=item.label("meshid").ifBlank {if(action=="nodemeshchange") item.label("newMeshId") else ""}.ifBlank {nested?.label("meshid").orEmpty()}
    val group=item.label("meshname").ifBlank {if(action in setOf("removenode","nodemeshchange")) arg(1) else ""}
    return EventDevice(raw.ifBlank {nested?.label("_id").orEmpty()},name,mesh,group)
}
internal data class ServerEventInventory(val nodes:Map<String,EventDevice> = emptyMap(),val groups:Map<String,String> = emptyMap())
internal fun serverEventInventory(groups:List<Pair<MeshGroup,List<MeshNode>>>):ServerEventInventory {
    val names=groups.mapNotNull {(group,_)->eventKey(group.id,"mesh").takeIf {it.isNotBlank()}?.let {it to group.name}}.toMap()
    val nodes=groups.flatMap {(group,items)->items.mapNotNull {node->eventKey(node.id,"node").takeIf {it.isNotBlank()}?.let {it to EventDevice(node.id,node.name,node.meshid.ifBlank {group.id})}}}.toMap()
    return ServerEventInventory(nodes,names)
}
internal fun ServerEventRecord.withInventory(inventory:ServerEventInventory):ServerEventRecord {
    val node=inventory.nodes[eventKey(nodeId,"node")]
    val name=nodeName.ifBlank {node?.name.orEmpty()}
    val mesh=meshId.ifBlank {node?.meshId.orEmpty()}
    val group=groupName.ifBlank {inventory.groups[eventKey(mesh,"mesh")].orEmpty()}
    return copy(nodeName=name,meshId=mesh,groupName=group,inventoryResolved=inventoryResolved || name!=nodeName || mesh!=meshId || group!=groupName)
}
internal fun ServerEventRecord.deviceLabel():String=if(nodeName.isBlank()) "" else nodeName+if(groupName.isBlank()) "" else "($groupName)"
