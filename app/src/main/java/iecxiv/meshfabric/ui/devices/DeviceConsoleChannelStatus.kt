package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
@Composable internal fun DeviceConsoleChannelStatus(phase:String,connected:Boolean,onReconnect:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    Row(Modifier.fillMaxWidth().testTag("agent_console_status_row"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(when(phase) {
            "connecting","checking"->t("Verificando canal, agente y permiso de consola…","Verifying channel, agent and console permission…")
            "checking_send"->t("Reverificando antes de enviar…","Rechecking before sending…")
            "ready"->t("Canal verificado","Channel verified")
            else->t("Consola desconectada","Console disconnected")
        },style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f).testTag("agent_console_status"))
        if(phase=="closed") TextButton(enabled=connected,onClick=onReconnect,modifier=Modifier.heightIn(min=48.dp).testTag("agent_console_reconnect")) {Text(t("Reconectar","Reconnect"))}
    }
}
