package iecxiv.meshfabric.ui.devices
import android.content.*
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.*
@Composable internal fun DeviceRunFeedbackSubview(node:MeshNode,controller:RemoteActionFeedback) {
    val state by controller.state.collectAsState()
    val command=state.commands.find {it.id==state.commandWindow} ?: return
    val context=LocalContext.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var copyStatus by remember(command.id) {mutableStateOf("")}
    DeviceGeneralFrame(node,controller::closeCommand,inline=true,subtitle=command.title,tagPrefix="device_run_output",body={
        SelectionContainer {Text(command.command,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,maxLines=3,overflow=TextOverflow.Ellipsis)}
        if(command.busy) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
        Text(command.message,color=if(command.phase==CommandPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if(command.output.isNotEmpty()) OutlinedTextField(command.output,{},readOnly=true,label={Text(t("Salida","Output"))},modifier=Modifier.fillMaxWidth().heightIn(min=160.dp,max=260.dp),minLines=6,maxLines=12,textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
        if(copyStatus.isNotEmpty()) Text(copyStatus,style=MaterialTheme.typography.bodySmall)
        Text(t("Cerrar no detiene el comando remoto. Recibir salida no demuestra que el proceso haya terminado correctamente.","Closing does not stop the remote command. Receiving output does not prove successful completion."),style=MaterialTheme.typography.bodySmall)
    },footer={
        TextButton(enabled=command.output.isNotEmpty(),onClick={
            copyStatus=if(runCatching {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip=ClipData.newPlainText(t("Salida remota","Remote output"),command.output)
                clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                clipboard.setPrimaryClip(clip)
            }.isSuccess) t("Salida copiada; puede contener datos sensibles.","Output copied; it may contain sensitive data.") else t("No se pudo copiar.","Copy failed.")
        }) {Text(t("Copiar salida","Copy output"))}
        TextButton(onClick=controller::closeCommand) {Text(t("Volver","Back"))}
    })
}
