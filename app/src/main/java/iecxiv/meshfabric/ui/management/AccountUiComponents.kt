package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable internal fun ManagementDialog(
    onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,
    dismissButton:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,
    title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,
    shape:Shape=RoundedCornerShape(16.dp),containerColor:Color=MaterialTheme.colorScheme.surface,
    iconContentColor:Color=MaterialTheme.colorScheme.primary,titleContentColor:Color=MaterialTheme.colorScheme.onSurface,
    textContentColor:Color=MaterialTheme.colorScheme.onSurfaceVariant,tonalElevation:Dp=6.dp,
    properties:DialogProperties=DialogProperties()
) {
    AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,
        modifier=modifier.border(BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shape),
        dismissButton=dismissButton,icon=icon,title={
            if(title!=null) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold)) { title() }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        },text=text,shape=shape,containerColor=containerColor,iconContentColor=iconContentColor,
        titleContentColor=titleContentColor,textContentColor=textContentColor,tonalElevation=tonalElevation,properties=properties)
}
@Composable internal fun AccountActionsCard(content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal=12.dp,vertical=2.dp),verticalArrangement=Arrangement.spacedBy(0.dp),content=content)
    }
}
@Composable internal fun CompactAccountActionRow(label:String,icon:ImageVector,enabled:Boolean,dangerous:Boolean=false,onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp),
        colors=ButtonDefaults.textButtonColors(contentColor=if(dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
        Icon(icon,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp))
    }
}
@Composable internal fun AccountLoginHistoryRow(item:JSONObject,date:String,details:String,es:Boolean) {
    val outcome=accountLoginOutcome(item.opt("m"))
    val dark=MaterialTheme.colorScheme.surface.luminance()<0.5f
    val background=when(outcome) {
        true->if(dark) Color(0xFF173B25) else Color(0xFFE2F4E7)
        false->if(dark) Color(0xFF4A2025) else Color(0xFFFCE5E7)
        null->MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground=when(outcome) {
        true->if(dark) Color(0xFFC5F2D1) else Color(0xFF163B24)
        false->if(dark) Color(0xFFFFDADD) else Color(0xFF64232B)
        null->MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=background,contentColor=foreground) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(when(outcome) { true->Icons.Default.CheckCircle; false->Icons.Default.ErrorOutline; null->Icons.Default.Info },null,Modifier.size(18.dp))
                Text(loginHistoryLabel(item.opt("m"),es),style=MaterialTheme.typography.labelLarge)
            }
            if(date.isNotBlank()) Text(date,style=MaterialTheme.typography.bodySmall)
            if(details.isNotBlank()) Text(details,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable internal fun AccountCredentialRow(label:String,value:String,copyLabel:String,onCopy:()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(value,style=MaterialTheme.typography.bodyMedium,fontFamily=FontFamily.Monospace) }
            }
            IconButton(onClick=onCopy,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(Icons.Default.ContentCopy,contentDescription=copyLabel) }
        }
    }
}
@Composable internal fun AccountTokenChoice(token:JSONObject,selected:Boolean,enabled:Boolean,locale:String,hours24:Boolean,es:Boolean,onSelect:()->Unit,onCopy:()->Unit) {
    val id=token.optString("tokenUser")
    Surface(Modifier.fillMaxWidth().selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick=onSelect),
        shape=MaterialTheme.shapes.small,color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border=BorderStroke(1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            RadioButton(selected=selected,onClick=null,enabled=enabled)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(token.optString("name").ifBlank { if(es) "Sin nombre" else "Unnamed" },style=MaterialTheme.typography.titleSmall)
                Text(accountTokenExpiry(token,locale,hours24,es),style=MaterialTheme.typography.bodySmall)
                Text(id,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)
            }
            IconButton(onClick=onCopy,enabled=enabled,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                Icon(Icons.Default.ContentCopy,contentDescription=if(es) "Copiar ID del token" else "Copy token ID")
            }
        }
    }
}
