package iecxiv.meshfabric.ui.management

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun ActionFeedbackDialogs(controller:RemoteActionFeedback) {
    val state by controller.state.collectAsState()
    val context=LocalContext.current
    state.commands.find { it.id==state.commandWindow }?.let { command->
        var copied by remember(command.id) { mutableStateOf(false) }
        FabricDialog(title=command.title,onDismiss=controller::closeCommand,confirm=managementText("Copiar salida","Copy output"),enabled=command.output.isNotEmpty(),
            onConfirm={
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(managementText("Salida remota","Remote output"),command.output)); copied=true
            },cancel=managementText("Cerrar","Close")) {
            SelectionContainer { Text(command.command,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,maxLines=3,overflow=TextOverflow.Ellipsis) }
            if(command.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(command.message,color=if(command.phase==CommandPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if(command.output.isNotEmpty()) key(command.id) {
                OutlinedTextField(value=command.output,onValueChange={},readOnly=true,label={ Text(managementText("Salida","Output")) },
                    modifier=Modifier.fillMaxWidth().heightIn(min=160.dp,max=260.dp),minLines=6,maxLines=12,
                    textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
            }
            if(copied) Text(managementText("Salida copiada","Output copied"),color=MaterialTheme.colorScheme.primary)
            Text(managementText("Cerrar esta ventana no detiene el comando remoto. Salida recibida no implica que el proceso haya terminado correctamente.","Closing this window does not stop the remote command. Receiving output does not imply that the process completed successfully."),style=MaterialTheme.typography.bodySmall)
        }
    }
    if(state.clipboardWindow) state.clipboard?.let { clipboard->
        FabricDialog(title=clipboard.title,onDismiss=controller::closeClipboard,confirm=managementText("Cerrar","Close"),onConfirm=controller::closeClipboard,cancel=null) {
            if(clipboard.phase==ClipboardPhase.VERIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(clipboard.message,color=if(clipboard.phase==ClipboardPhase.ERROR || clipboard.phase==ClipboardPhase.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(managementText("Canal: ${clipboard.transport}","Channel: ${clipboard.transport}"),style=MaterialTheme.typography.bodySmall)
            Text(managementText("No se abre un escritorio ni se simula Ctrl+V. La disponibilidad depende también de las políticas del servidor y de la sesión gráfica remota.","No desktop is opened and Ctrl+V is not simulated. Availability also depends on server policies and the remote graphical session."),style=MaterialTheme.typography.bodySmall)
        }
    }
}
