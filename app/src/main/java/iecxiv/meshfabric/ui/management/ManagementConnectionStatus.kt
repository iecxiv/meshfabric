package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
@Composable internal fun ManagementConnectionStatus(ready:Boolean,connecting:Boolean,reconnectEnabled:Boolean=true,modifier:Modifier=Modifier,onReconnect:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val labels=if(es) listOf("Conectando con el canal de gestión...","Canal de gestión conectado. El servidor valida los permisos.","Canal de gestión desconectado.") else listOf("Connecting to the management channel...","Management channel connected. The server validates permissions.","Management channel disconnected.")
    val active=if(ready) 1 else if(connecting) 0 else 2
    val showReconnect=!ready && !connecting
    Row(modifier.fillMaxWidth().testTag("management-connection-row"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f).testTag("management-connection-label").semantics {liveRegion=LiveRegionMode.Polite},contentAlignment=Alignment.CenterStart) {
            labels.forEachIndexed {index,label->
                val visibility=if(index==active) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
                Text(label,visibility,style=MaterialTheme.typography.bodySmall,color=if(active==2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.testTag("management-reconnect-slot")) {
            val visibility=if(showReconnect) Modifier.testTag("management-reconnect") else Modifier.alpha(0f).clearAndSetSemantics {}
            TextButton(onClick=onReconnect,enabled=showReconnect && reconnectEnabled,modifier=visibility.heightIn(min=48.dp)) {Text(if(es) "Reconectar" else "Reconnect")}
        }
    }
}
