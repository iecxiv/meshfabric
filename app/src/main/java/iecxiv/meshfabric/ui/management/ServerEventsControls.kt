package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
@Composable internal fun ServerEventsControls(selection:ServerEventsQuery,enabled:Boolean,onSelection:(ServerEventsQuery)->Unit) {
    var typesOpen by remember {mutableStateOf(false)}
    var limitsOpen by remember {mutableStateOf(false)}
    val labels=stringArrayResource(R.array.log_event_types)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick={typesOpen=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("log-event-type")) {Text(stringResource(R.string.log_event_filter)+": "+labels[selection.type.ordinal])}
            DropdownMenu(expanded=typesOpen,onDismissRequest={typesOpen=false}) {
                ServerEventType.entries.forEach {type->DropdownMenuItem(text={Text(labels[type.ordinal])},modifier=Modifier.testTag("log-event-type-"+type.wire.ifBlank {"all"}),onClick={typesOpen=false;onSelection(selection.copy(type=type))})}
            }
        }
        Box(Modifier.weight(1f)) {
            val label=selection.limit?.let {stringResource(R.string.log_event_latest,it)} ?: stringResource(R.string.log_event_unlimited)
            OutlinedButton(onClick={limitsOpen=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("log-event-count")) {Text(stringResource(R.string.log_event_show)+": "+label)}
            DropdownMenu(expanded=limitsOpen,onDismissRequest={limitsOpen=false}) {
                serverEventLimitChoices.forEach {value->DropdownMenuItem(text={Text(if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value))},modifier=Modifier.testTag("log-event-limit-$value"),onClick={limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))})}
            }
        }
    }
    if(selection.limit==null) Text(stringResource(R.string.log_event_safety),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun ServerEventIcon(action:String) {
    val type=ServerEventType.fromAction(action)
    val icon=when(type) {
        ServerEventType.ALL->Icons.Default.History
        ServerEventType.AGENT->Icons.Default.Computer
        ServerEventType.RELAY->Icons.Default.SwapHoriz
        ServerEventType.MANUAL->Icons.Default.Description
        ServerEventType.COMMAND->Icons.Default.Code
        ServerEventType.UPLOAD->Icons.Default.CloudUpload
        ServerEventType.CHANGE->Icons.Default.Edit
        ServerEventType.REMOVE->Icons.Default.Delete
    }
    val description=if(type==ServerEventType.ALL) stringResource(R.string.log_event) else stringArrayResource(R.array.log_event_types)[type.ordinal]
    Icon(icon,description,Modifier.size(20.dp).testTag("log-event-icon-"+type.wire.ifBlank {"other"}),tint=MaterialTheme.colorScheme.primary)
}
