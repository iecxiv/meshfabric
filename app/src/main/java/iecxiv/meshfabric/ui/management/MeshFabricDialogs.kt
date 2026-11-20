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
import androidx.compose.ui.platform.LocalConfiguration
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
    ManagementDialog(onDismissRequest=onDismiss,title={ Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp) },
        text={ Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton={
            if(dangerous) Button(onClick=onConfirm,enabled=enabled,colors=ButtonDefaults.buttonColors(
                containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) { Text(confirm,fontWeight=FontWeight.Bold) }
            else TextButton(onClick=onConfirm,enabled=enabled) { Text(confirm,fontWeight=FontWeight.Bold) }
        },
        dismissButton={ if(cancel!=null) TextButton(onClick=onDismiss) { Text(cancel) } })
}
@Composable internal fun DeviceNotesDialog(notes:DeviceNotes,onClose:()->Unit,onEdit:()->Unit,onChange:(String)->Unit,onSave:()->Unit,onReload:()->Unit,connected:Boolean=true) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var discard by remember(notes.nodeId) { mutableStateOf(false) }
    var wordWrap by remember(notes.nodeId) { mutableStateOf(true) }
    val busy=notes.phase==NotesPhase.LOADING || notes.phase==NotesPhase.SAVING
    val close:()->Unit={ if(notes.phase!=NotesPhase.SAVING) { if(notes.dirty) discard=true else onClose() } }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        iecxiv.meshfabric.ui.components.AppDialogSurface(shape = iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,color = iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),tonalElevation = iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
            modifier=Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f).padding(vertical=8.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text((if(notes.editable) t("Editar notas","Edit notes") else t("Notas","Notes"))+" · "+notes.name,
                        fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.weight(1f))
                    if(notes.editable) FilterChip(selected=wordWrap,onClick={ wordWrap=!wordWrap },
                        label={ Text(stringResource(R.string.word_wrap),fontSize=11.sp) },
                        leadingIcon={ Icon(Icons.AutoMirrored.Filled.WrapText,contentDescription=stringResource(R.string.word_wrap),modifier=Modifier.size(16.dp)) })
                }
                Spacer(Modifier.height(10.dp))
                if(busy) {
                    iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
                    Text(if(notes.phase==NotesPhase.LOADING) t("Cargando notas del equipo…","Loading device notes…") else t("Verificando el guardado con el servidor…","Verifying the save with the server…"),
                        style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                if(notes.loaded) {
                    if(notes.editable) {
                        NumberedTextEditor(value=notes.draft,onValueChange=onChange,wordWrap=wordWrap,
                            enabled=!busy,modifier=Modifier.fillMaxWidth().weight(1f))
                    } else {
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(notes.original.ifEmpty { t("Este equipo no tiene notas.","This device has no notes.") },
                                    modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.fillMaxWidth().heightIn(max=112.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(!connected) Text(t("Sin conexión: el borrador permanece local y no se puede guardar.","Offline: the draft remains local and cannot be saved."),color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                    if(notes.editable && notes.loaded) Text(t("Guardar el campo vacío elimina las notas existentes.","Saving an empty field deletes the existing notes."),style=MaterialTheme.typography.bodySmall)
                    if(notes.error.isNotBlank()) {
                        Text(notes.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        if(!notes.loaded) TextButton(onClick=onReload,enabled=!busy) { Text(t("Reintentar carga","Retry loading")) }
                    }
                    if(notes.message.isNotBlank()) Text(notes.message,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=close,enabled=notes.phase!=NotesPhase.SAVING,modifier=Modifier.height(36.dp)) {
                        Text(if(notes.editable) stringResource(R.string.cancel) else t("Cerrar","Close"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick=if(notes.editable) onSave else onEdit,
                        enabled=if(notes.editable) notes.canSave && connected else notes.loaded && !busy,
                        modifier=Modifier.height(38.dp)) {
                        Text(if(notes.editable) stringResource(R.string.save_changes) else t("Editar","Edit"),fontSize=12.sp)
                    }
                }
            }
        }
    }
    if(discard) FabricDialog(t("Descartar cambios","Discard changes"),{ discard=false },t("Descartar","Discard"),onConfirm={ discard=false; onClose() }) {
        Text(t("Se perderán los cambios locales de las notas de ${notes.name}. No se enviará ningún guardado.","Local changes to the notes for ${notes.name} will be lost. No save request will be sent."))
    }
}
