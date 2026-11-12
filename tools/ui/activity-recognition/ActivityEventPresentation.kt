package iecxiv.meshfabric.ui.management

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.google.gson.JsonParser
import org.json.JSONObject

internal fun activityEventTitle(action:String):String = when(action) {
    "nodeconnect" -> "Equipo conectado"
    "nodedisconnect" -> "Equipo desconectado"
    "nodemeshchange" -> "Equipo movido de grupo"
    "meshchange" -> "Grupo actualizado"
    "createmesh" -> "Grupo creado"
    "deletemesh" -> "Grupo eliminado"
    "accountchange" -> "Cuenta actualizada"
    "accountcreate" -> "Cuenta creada"
    "accountremove" -> "Cuenta eliminada"
    else -> when(ServerEventType.fromAction(action)) {
        ServerEventType.AGENT -> "Registro del agente"
        ServerEventType.RELAY -> "Registro de conexión remota"
        ServerEventType.MANUAL -> "Evento manual"
        ServerEventType.COMMAND -> "Comando remoto"
        ServerEventType.UPLOAD -> "Carga de archivos"
        ServerEventType.CHANGE -> "Datos del equipo actualizados"
        ServerEventType.REMOVE -> "Equipo eliminado"
        ServerEventType.ALL -> "Evento del servidor"
    }
}
internal fun activityEventDevice(packet:JSONObject):EventDevice {
    val device=serverEventDevice(JsonParser.parseString(packet.toString()).asJsonObject)
    return if(device.nodeId.isBlank()) device.copy(nodeId=packet.opt("node") as? String ?: "") else device
}
internal fun activityEventMessage(packet:JSONObject):String = when(val value=packet.opt("msg")) {
    is String -> value
    is Number -> value.toString()
    else -> ""
}.ifBlank { "El servidor no incluyó una descripción adicional." }.take(1000)
internal fun resolveActivityEntry(entry:ActivityEntry,inventory:ServerEventInventory):ActivityEntry {
    val record=ServerEventRecord(entry.action,"",entry.actor,entry.nodeId,entry.deviceName,null,
        meshId=entry.meshId,groupName=entry.groupName).withInventory(inventory)
    return entry.copy(deviceName=record.nodeName,meshId=record.meshId,groupName=record.groupName)
}
internal fun activityIconKey(entry:ActivityEntry):String = when {
    entry.action=="toast" -> "notification"
    entry.action=="msg" -> "message"
    entry.action in setOf("poweraction","wakedevices") -> "power"
    entry.action in setOf("nodeconnect","nodedisconnect") -> "connection"
    entry.action in setOf("deletemesh","accountremove") -> "remove"
    entry.action in setOf("nodemeshchange","meshchange","createmesh","accountchange","accountcreate") -> "change"
    ServerEventType.fromAction(entry.action)!=ServerEventType.ALL -> ServerEventType.fromAction(entry.action).wire
    entry.kind==ActivityKind.MESSAGE -> "message"
    else -> "unknown"
}
@Composable internal fun ActivityEntryIcon(entry:ActivityEntry) {
    val key=activityIconKey(entry)
    val image=when(key) {
        "notification" -> Icons.Default.Notifications
        "message" -> Icons.Default.ChatBubbleOutline
        "power" -> Icons.Default.PowerSettingsNew
        "connection" -> Icons.Default.Computer
        "change" -> Icons.Default.Edit
        "remove" -> Icons.Default.Delete
        "unknown" -> Icons.AutoMirrored.Filled.HelpOutline
        else -> serverEventImage(ServerEventType.fromAction(entry.action))
    }
    Icon(image,entry.title,Modifier.size(20.dp).testTag("activity-icon-$key"),tint=MaterialTheme.colorScheme.primary)
}
