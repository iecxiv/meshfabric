package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.NumberedTextEditor

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
    var wordWrap by remember(notes.nodeId) { mutableStateOf(true) }
    val busy=notes.phase==NotesPhase.LOADING || notes.phase==NotesPhase.SAVING
    val close:()->Unit={ if(notes.phase!=NotesPhase.SAVING) { if(notes.dirty) discard=true else onClose() } }
    if(notes.editable) {
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=6.dp,
                modifier=Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f).padding(vertical=8.dp)) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Editar notas · ${notes.name}",fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.weight(1f))
                        FilterChip(selected=wordWrap,onClick={ wordWrap=!wordWrap },
                            label={ Text(stringResource(R.string.word_wrap),fontSize=11.sp) },
                            leadingIcon={ Icon(Icons.AutoMirrored.Filled.WrapText,contentDescription=stringResource(R.string.word_wrap),modifier=Modifier.size(16.dp)) })
                    }
                    Spacer(Modifier.height(10.dp))
                    if(busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if(notes.phase==NotesPhase.LOADING) "Cargando notas del equipo…" else "Verificando el guardado con el servidor…",
                            style=MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    if(notes.loaded) {
                        NumberedTextEditor(value=notes.draft,onValueChange=onChange,wordWrap=wordWrap,
                            enabled=!busy,modifier=Modifier.fillMaxWidth().weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max=112.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(!connected) Text("Sin conexión: el borrador permanece local y no se puede guardar.",color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                        if(notes.loaded) Text("Guardar el campo vacío elimina las notas existentes.",style=MaterialTheme.typography.bodySmall)
                        if(notes.error.isNotBlank()) {
                            Text(notes.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            if(!notes.loaded) TextButton(onClick=onReload,enabled=!busy) { Text("Reintentar carga") }
                        }
                        if(notes.message.isNotBlank()) Text(notes.message,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick=close,enabled=notes.phase!=NotesPhase.SAVING,modifier=Modifier.height(36.dp)) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick=onSave,enabled=notes.canSave && connected,modifier=Modifier.height(38.dp)) {
                            Text(stringResource(R.string.save_changes),fontSize=12.sp)
                        }
                    }
                }
            }
        }
    } else {
        FabricDialog(title="Notas · "+notes.name,onDismiss=close,confirm="Editar",enabled=notes.loaded && !busy,
            onConfirm=onEdit,cancel="Cerrar") {
            if(!connected) Text("Sin conexión: el borrador permanece local y no se puede guardar.",color=MaterialTheme.colorScheme.error)
            when(notes.phase) {
                NotesPhase.LOADING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Cargando notas del equipo…") }
                NotesPhase.SAVING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Verificando el guardado con el servidor…") }
                else -> Unit
            }
            if(notes.loaded) SelectionContainer { Text(notes.original.ifEmpty { "Este equipo no tiene notas." },style=MaterialTheme.typography.bodyMedium) }
            if(notes.error.isNotBlank()) {
                Text(notes.error,color=MaterialTheme.colorScheme.error)
                if(!notes.loaded) TextButton(onClick=onReload) { Text("Reintentar carga") }
            }
            if(notes.message.isNotBlank()) Text(notes.message,color=MaterialTheme.colorScheme.primary)
        }
    }
    if(discard) FabricDialog("Descartar cambios",{ discard=false },"Descartar",onConfirm={ discard=false; onClose() }) {
        Text("Se perderán los cambios locales de las notas de ${notes.name}. No se enviará ningún guardado.")
    }
}
