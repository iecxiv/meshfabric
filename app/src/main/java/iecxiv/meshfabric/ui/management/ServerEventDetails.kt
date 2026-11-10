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
internal fun serverEventDetails(item:JsonObject):Map<String,String> = listOf("msgid","protocol","duration","ip","raddr","bytesin","bytesout").mapNotNull {key->
    item.get(key)?.takeIf {it.isJsonPrimitive}?.asString?.let {key to it}
}.toMap()
@Composable internal fun ServerEventDetailsDialog(row:ServerEventRecord,onDismiss:()->Unit) {
    val missing=stringResource(R.string.log_detail_missing)
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("log-event-details"),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),
        title={Text(stringResource(R.string.log_detail_title))},
        text={SelectionContainer {Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ServerEventIcon(row.action)
            ManagementInfoRow(stringResource(R.string.log_detail_date),row.time?.let {DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.LONG).format(Date(it))} ?: missing)
            ManagementInfoRow(stringResource(R.string.log_detail_type),row.action.ifBlank {missing})
            ManagementInfoRow(stringResource(R.string.log_detail_actor),row.actor.ifBlank {missing})
            ManagementInfoRow(stringResource(R.string.log_detail_device),row.nodeName.ifBlank {missing})
            if(row.nodeId.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_node),row.nodeId)
            Text(stringResource(R.string.log_detail_message),style=MaterialTheme.typography.labelMedium)
            Text(row.message.ifBlank {missing},Modifier.testTag("log-event-details-message"),style=MaterialTheme.typography.bodyMedium)
            row.details.forEach {(key,value)->ManagementInfoRow(key,value)}
        }}},confirmButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("log-event-details-close")) {Text(stringResource(R.string.log_detail_close))}})
}
