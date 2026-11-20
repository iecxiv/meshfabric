package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun GeneralValueRow(label:String,value:String,enabled:Boolean,tag:String,editLabel:String,onEdit:()->Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick=onEdit,enabled=enabled,modifier=Modifier.testTag("${tag}_edit")) {Icon(Icons.Default.Edit,editLabel,modifier=Modifier.size(18.dp))}
    }
}

@Composable internal fun GeneralFieldDialog(label:String,initialValue:String,multiline:Boolean,enabled:Boolean,es:Boolean,onDismiss:()->Unit,onApply:(String)->Boolean) {
    var value by remember {mutableStateOf(initialValue)}
    var invalid by remember {mutableStateOf(false)}
    fun text(a:String,b:String)=if(es) a else b
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=onDismiss,
        title={Text(text("Editar: ","Edit: ")+label,fontSize=18.sp,fontWeight=FontWeight.Bold)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value,{value=it;invalid=false},label={Text(label)},singleLine=!multiline,
                minLines=if(multiline) 2 else 1,maxLines=if(multiline) 6 else 1,
                modifier=Modifier.fillMaxWidth().testTag("general_field_input"))
            Text(text("Aplicar modifica el borrador local. Después usa Revisar guardado en General para enviarlo al servidor.","Apply updates the local draft. Then use Review save in General to send it to the server."),style=MaterialTheme.typography.bodySmall)
            if(!enabled) Text(text("No se puede aplicar sin datos y permisos verificados.","Cannot apply without verified data and permissions."),color=MaterialTheme.colorScheme.error)
            if(invalid) Text(text("Valor no válido. Revisa el formato y la longitud del campo.","Invalid value. Check the field format and length."),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("general_field_error"))
        }},
        confirmButton={TextButton(enabled=enabled,onClick={invalid=!onApply(value)},modifier=Modifier.testTag("general_field_apply")) {Text(text("Aplicar al borrador","Apply to draft"))}},
        dismissButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("general_field_cancel")) {Text(text("Cancelar","Cancel"))}})
}
