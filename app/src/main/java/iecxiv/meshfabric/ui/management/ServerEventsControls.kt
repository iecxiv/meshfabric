package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
internal fun serverEventImage(type:ServerEventType):ImageVector=when(type) {
    ServerEventType.ALL->Icons.Default.History
    ServerEventType.AGENT->Icons.Default.Computer
    ServerEventType.RELAY->Icons.Default.SwapHoriz
    ServerEventType.MANUAL->Icons.Default.Description
    ServerEventType.COMMAND->Icons.Default.Code
    ServerEventType.UPLOAD->Icons.Default.CloudUpload
    ServerEventType.CHANGE->Icons.Default.Edit
    ServerEventType.REMOVE->Icons.Default.Delete
}
@Composable private fun EventFilterButton(label:String,tag:String,enabled:Boolean,icon:ImageVector?=null,onClick:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag),shape=MaterialTheme.shapes.medium,
        colors=ButtonDefaults.outlinedButtonColors(containerColor=scheme.surfaceVariant,contentColor=scheme.onSurface),border=BorderStroke(1.dp,scheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            if(icon!=null) Icon(icon,null,Modifier.size(20.dp).align(Alignment.CenterStart))
            Text(label,Modifier.fillMaxWidth().padding(horizontal=24.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown,null,Modifier.size(20.dp).align(Alignment.CenterEnd))
        }
    }
}
@Composable internal fun ServerEventsControls(selection:ServerEventsQuery,enabled:Boolean,onSelection:(ServerEventsQuery)->Unit) {
    var typesOpen by remember {mutableStateOf(false)}
    var limitsOpen by remember {mutableStateOf(false)}
    val labels=stringArrayResource(R.array.log_event_types)
    val scheme=MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val selectorWidth=minOf((maxWidth-8.dp)/2,280.dp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(selectorWidth)) {
                EventFilterButton(labels[selection.type.ordinal],"log-event-type",enabled,serverEventImage(selection.type)) {typesOpen=true}
                DropdownMenu(expanded=typesOpen,onDismissRequest={typesOpen=false},modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-type-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    ServerEventType.entries.forEach {type->DropdownMenuItem(text={Text(labels[type.ordinal],style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingIcon={Icon(serverEventImage(type),null,Modifier.size(20.dp).testTag("log-event-type-icon-"+type.wire.ifBlank {"all"}))},modifier=Modifier.testTag("log-event-type-"+type.wire.ifBlank {"all"}),onClick={typesOpen=false;onSelection(selection.copy(type=type))})}
                }
            }
            Box(Modifier.width(selectorWidth)) {
                val label=selection.limit?.let {stringResource(R.string.log_event_latest,it)} ?: stringResource(R.string.log_event_unlimited)
                EventFilterButton(label,"log-event-count",enabled) {limitsOpen=true}
                DropdownMenu(expanded=limitsOpen,onDismissRequest={limitsOpen=false},modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-count-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    serverEventLimitChoices.forEach {value->DropdownMenuItem(text={Text(if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value),Modifier.fillMaxWidth().testTag("log-event-limit-text-$value"),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)},modifier=Modifier.testTag("log-event-limit-$value"),onClick={limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))})}
                }
            }
        }
    }
    if(selection.limit==null) Text(stringResource(R.string.log_event_safety),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun ServerEventIcon(action:String) {
    val type=ServerEventType.fromAction(action)
    val description=if(type==ServerEventType.ALL) stringResource(R.string.log_event) else stringArrayResource(R.array.log_event_types)[type.ordinal]
    Icon(serverEventImage(type),description,Modifier.size(20.dp).testTag("log-event-icon-"+type.wire.ifBlank {"other"}),tint=MaterialTheme.colorScheme.primary)
}
