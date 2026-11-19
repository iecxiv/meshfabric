package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.ui.components.AppAlertDialog
import iecxiv.meshfabric.ui.theme.LocalAppDarkTheme

@Composable internal fun DeviceBatchAlertDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,
    text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit) {
    AppAlertDialog(onDismissRequest=onDismissRequest,
        title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Devices,contentDescription=null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
            Box(Modifier.weight(1f)) { title() }
        }},text=text,textContentColor=MaterialTheme.colorScheme.onSurface,
        confirmButton=confirmButton,dismissButton=dismissButton)
}
@Composable internal fun DeviceBatchNotice(text:String) {
    Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small,
        color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
            Icon(Icons.Default.Info,contentDescription=null,modifier=Modifier.size(18.dp))
            Text(text,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable internal fun DeviceBatchSecondary(text:String) {
    Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun DeviceBatchSummary(text:String) {
    Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
}
@Composable internal fun DeviceBatchStatus(text:String,kind:DeviceBatchVisualKind) {
    val scheme=MaterialTheme.colorScheme
    val dark=LocalAppDarkTheme.current
    val color=when(kind) {
        DeviceBatchVisualKind.PROGRESS->scheme.primary
        DeviceBatchVisualKind.VERIFIED->if(dark) Color(0xFF81C784) else Color(0xFF1B5E20)
        DeviceBatchVisualKind.WARNING->if(dark) Color(0xFFFFE082) else Color(0xFF8A4B00)
        DeviceBatchVisualKind.ERROR->scheme.error
        DeviceBatchVisualKind.NEUTRAL->scheme.onSurfaceVariant
    }
    val icon=when(kind) {
        DeviceBatchVisualKind.PROGRESS->Icons.Default.Info
        DeviceBatchVisualKind.VERIFIED->Icons.Default.CheckCircle
        DeviceBatchVisualKind.WARNING->Icons.Default.Warning
        DeviceBatchVisualKind.ERROR->Icons.Default.ErrorOutline
        DeviceBatchVisualKind.NEUTRAL->Icons.Default.RemoveCircleOutline
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top) {
        Icon(icon,contentDescription=null,tint=color,modifier=Modifier.size(16.dp))
        Text(text,modifier=Modifier.weight(1f),color=color,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium)
    }
}
