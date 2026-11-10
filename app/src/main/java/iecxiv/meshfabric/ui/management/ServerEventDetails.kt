package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.google.gson.JsonObject
import iecxiv.meshfabric.R
import java.text.DateFormat
import java.util.Date
internal fun serverEventDetails(item:JsonObject):Map<String,String> {
    val action=item.get("action")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    val keys=listOf("msgid","protocol","duration","ip","raddr","bytesin","bytesout")+
        (if(action=="runcommands") listOf("cmds","cmdType","runAsUser") else emptyList())+
        (if(action=="nodemeshchange") listOf("oldMeshId","newMeshId") else emptyList())
    return keys.mapNotNull {key->item.get(key)?.takeIf {it.isJsonPrimitive && when(key) {
        "cmds","oldMeshId","newMeshId"->it.asJsonPrimitive.isString
        "cmdType","runAsUser"->it.asJsonPrimitive.isNumber
        else->true
    }}?.asString?.let {key to it}}.toMap()
}
@Composable internal fun ServerEventDetailsDialog(row:ServerEventRecord,onDismiss:()->Unit) {
    val missing=stringResource(R.string.log_detail_missing)
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("log-event-details"),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),
        title={Text(stringResource(R.string.log_detail_title))},
        text={SelectionContainer {Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ServerEventIcon(row.action)
            ManagementInfoRow(stringResource(R.string.log_detail_date),row.time?.let {DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.LONG).format(Date(it))} ?: missing)
            ManagementInfoRow(stringResource(R.string.log_detail_type),row.action.ifBlank {missing})
            ManagementInfoRow(stringResource(R.string.log_detail_actor),row.actor.ifBlank {missing})
            Text(stringResource(R.string.log_detail_device),style=MaterialTheme.typography.labelMedium)
            Text(row.deviceLabel().ifBlank {missing},Modifier.testTag("log-event-details-device"),style=MaterialTheme.typography.bodyMedium)
            if(row.groupName.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_group),row.groupName)
            if(row.meshId.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_mesh),row.meshId)
            if(row.nodeId.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_node),row.nodeId)
            Text(stringResource(R.string.log_detail_message),style=MaterialTheme.typography.labelMedium)
            Text(row.message.ifBlank {missing},Modifier.testTag("log-event-details-message"),style=MaterialTheme.typography.bodyMedium)
            if(row.action=="runcommands") {
                Text(stringResource(R.string.log_detail_command),style=MaterialTheme.typography.labelMedium)
                Text(row.details["cmds"] ?: missing,Modifier.testTag("log-event-command"),style=MaterialTheme.typography.bodyMedium)
                row.details["cmdType"]?.let {ManagementInfoRow(stringResource(R.string.log_detail_cmd_type),it)}
                row.details["runAsUser"]?.let {ManagementInfoRow(stringResource(R.string.log_detail_run_as),it)}
            }
            row.details.filterKeys {it !in setOf("cmds","cmdType","runAsUser")}.forEach {(key,value)->ManagementInfoRow(key,value)}
        }}},confirmButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("log-event-details-close")) {Text(stringResource(R.string.log_detail_close))}})
}
