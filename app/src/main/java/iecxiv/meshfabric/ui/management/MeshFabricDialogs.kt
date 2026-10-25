package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R

@Composable internal fun FabricDialog(title:String,onDismiss:()->Unit,confirm:String,enabled:Boolean=true,
    onConfirm:()->Unit,cancel:String?=stringResource(R.string.cancel),dangerous:Boolean=false,content:@Composable ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={ Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp) },
        text={ Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton={
            if(dangerous) Button(onClick=onConfirm,enabled=enabled,colors=ButtonDefaults.buttonColors(
                containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) { Text(confirm,fontWeight=FontWeight.Bold) }
            else TextButton(onClick=onConfirm,enabled=enabled) { Text(confirm,fontWeight=FontWeight.Bold) }
        },
        dismissButton={ if(cancel!=null) TextButton(onClick=onDismiss) { Text(cancel) } })
}
@Composable internal fun DeviceNotesDialog(notes:DeviceNotes,onClose:()->Unit,onEdit:()->Unit,onChange:(String)->Unit,onSave:()->Unit,onReload:()->Unit,connected:Boolean=true) {
    var discard by remember(notes.nodeId) { mutableStateOf(false) }
    val busy=notes.phase==NotesPhase.LOADING || notes.phase==NotesPhase.SAVING
    val close:()->Unit={ if(notes.phase!=NotesPhase.SAVING) { if(notes.dirty) discard=true else onClose() } }
    FabricDialog(title=(if(notes.editable) "Editar notas" else "Notas")+" · "+notes.name,onDismiss=close,
        confirm=if(notes.editable) "Guardar" else "Editar",enabled=if(notes.editable) notes.canSave && connected else notes.loaded && !busy,
        onConfirm=if(notes.editable) onSave else onEdit,cancel=if(notes.editable) "Cancelar" else "Cerrar") {
        if(!connected) Text("Sin conexión: el borrador permanece local y no se puede guardar.",color=MaterialTheme.colorScheme.error)
        when(notes.phase) {
            NotesPhase.LOADING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Cargando notas del equipo…") }
            NotesPhase.SAVING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Verificando el guardado con el servidor…") }
            else -> Unit
        }
        if(notes.loaded) {
            if(notes.editable) OutlinedTextField(notes.draft,onChange,Modifier.fillMaxWidth(),label={ Text("Notas") },
                enabled=!busy,minLines=6,maxLines=12,supportingText={ Text("Guardar el campo vacío elimina las notas existentes.") })
            else SelectionContainer { Text(notes.original.ifEmpty { "Este equipo no tiene notas." },style=MaterialTheme.typography.bodyMedium) }
        }
        if(notes.error.isNotBlank()) {
            Text(notes.error,color=MaterialTheme.colorScheme.error)
            if(!notes.loaded) TextButton(onClick=onReload) { Text("Reintentar carga") }
        }
        if(notes.message.isNotBlank()) Text(notes.message,color=MaterialTheme.colorScheme.primary)
    }
    if(discard) FabricDialog("Descartar cambios",{ discard=false },"Descartar",onConfirm={ discard=false; onClose() }) {
        Text("Se perderán los cambios locales de las notas de ${notes.name}. No se enviará ningún guardado.")
    }
}
