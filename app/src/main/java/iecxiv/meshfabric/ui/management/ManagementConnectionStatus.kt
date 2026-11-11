package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
@Composable internal fun ManagementConnectionStatus(ready:Boolean,connecting:Boolean,reconnectEnabled:Boolean=true,modifier:Modifier=Modifier,onReconnect:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    val text=when {
        ready->if(es) "Canal de gestión conectado. El servidor valida los permisos." else "Management channel connected. The server validates permissions."
        connecting->if(es) "Conectando con el canal de gestión..." else "Connecting to the management channel..."
        else->if(es) "Canal de gestión desconectado." else "Management channel disconnected."
    }
    val showReconnect=!ready && !connecting
    Row(modifier.fillMaxWidth().testTag("management-connection-row").padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f).testTag("management-connection-label").semantics {liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall,color=if(showReconnect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if(showReconnect) TextButton(onClick=onReconnect,enabled=reconnectEnabled,modifier=Modifier.testTag("management-reconnect").heightIn(min=48.dp)) {Text(if(es) "Reconectar" else "Reconnect")}
    }
}
