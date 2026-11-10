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
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),shape=MaterialTheme.shapes.medium,
        colors=ButtonDefaults.outlinedButtonColors(containerColor=scheme.surfaceVariant,contentColor=scheme.onSurface),border=BorderStroke(1.dp,scheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            if(icon!=null) Icon(icon,null,Modifier.size(20.dp).align(Alignment.CenterStart))
            Text(label,Modifier.fillMaxWidth().padding(horizontal=24.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,softWrap=true,overflow=TextOverflow.Clip)
            Icon(Icons.Default.ArrowDropDown,null,Modifier.size(20.dp).align(Alignment.CenterEnd))
        }
    }
}
@Composable internal fun ServerEventsControls(selection:ServerEventsQuery,enabled:Boolean,onSelection:(ServerEventsQuery)->Unit) {
    var typesOpen by remember {mutableStateOf(false)}
    var limitsOpen by remember {mutableStateOf(false)}
    val labels=stringArrayResource(R.array.log_event_types)
    val scheme=MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
    BoxWithConstraints(Modifier.widthIn(max=560.dp).fillMaxWidth().testTag("log-event-controls")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.weight(2f)) {
                val typeWidth=maxWidth
                EventFilterButton(labels[selection.type.ordinal],"log-event-type",enabled,serverEventImage(selection.type)) {typesOpen=true}
                DropdownMenu(expanded=typesOpen,onDismissRequest={typesOpen=false},modifier=Modifier.width(typeWidth).heightIn(max=280.dp).testTag("log-event-type-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    ServerEventType.entries.forEach {type->
                        val key=type.wire.ifBlank {"all"}
                        EventFilterOption(labels[type.ordinal],"log-event-type-$key","log-event-type-text-$key",serverEventImage(type)) {typesOpen=false;onSelection(selection.copy(type=type))}
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val countWidth=maxWidth
                val label=selection.limit?.let {stringResource(R.string.log_event_latest,it)} ?: stringResource(R.string.log_event_unlimited)
                EventFilterButton(label,"log-event-count",enabled) {limitsOpen=true}
                DropdownMenu(expanded=limitsOpen,onDismissRequest={limitsOpen=false},modifier=Modifier.width(countWidth).heightIn(max=280.dp).testTag("log-event-count-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    serverEventLimitChoices.forEach {value->
                        val label=if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value)
                        EventFilterOption(label,"log-event-limit-$value","log-event-limit-text-$value") {limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))}
                    }
                }
            }
        }
    }}
    if(selection.limit==null) Text(stringResource(R.string.log_event_safety),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun ServerEventIcon(action:String) {
    val type=ServerEventType.fromAction(action)
    val description=if(type==ServerEventType.ALL) stringResource(R.string.log_event) else stringArrayResource(R.array.log_event_types)[type.ordinal]
    Icon(serverEventImage(type),description,Modifier.size(20.dp).testTag("log-event-icon-"+type.wire.ifBlank {"other"}),tint=MaterialTheme.colorScheme.primary)
}

@Composable private fun EventFilterOption(label:String,tag:String,textTag:String,icon:ImageVector?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag).clickable(role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(icon!=null) {
            Icon(icon,null,Modifier.size(20.dp).testTag(tag.replace("log-event-type-","log-event-type-icon-")))
            Spacer(Modifier.width(12.dp))
        }
        Text(label,Modifier.weight(1f).testTag(textTag),textAlign=if(icon==null) TextAlign.Center else TextAlign.Start,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,softWrap=true,overflow=TextOverflow.Clip)
    }
}
