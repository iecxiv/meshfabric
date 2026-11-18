package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject

internal const val GENERAL_CONSENT_MASK=511L
internal fun generalConsentValue(node:JsonObject):Long {
    val raw=node.get("consent") ?: return 0L
    require(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber)
    val value=raw.asDouble
    require(value.isFinite() && value%1.0==0.0 && value>=0.0 && value<=4294967295.0)
    return value.toLong()
}
internal fun generalConsentMerge(before:Long,selected:Long):Long {
    require(before in 0L..0xffffffffL && selected>=0L && selected and GENERAL_CONSENT_MASK==selected)
    return (before and GENERAL_CONSENT_MASK.inv()) or selected
}
internal fun generalConsentOptions(es:Boolean)=if(es) listOf("Avisar: escritorio","Avisar: terminal","Avisar: archivos","Solicitar autorización: escritorio","Solicitar autorización: terminal","Solicitar autorización: archivos","Mostrar barra de conexión","Avisar: Registro","Solicitar autorización: Registro") else listOf("Notify: desktop","Notify: terminal","Notify: files","Prompt: desktop","Prompt: terminal","Prompt: files","Show connection toolbar","Notify: Registry","Prompt: Registry")
internal fun generalConsentLabel(value:Long,es:Boolean):String {
    val labels=generalConsentOptions(es).filterIndexed {i,_->value and (1L shl i)!=0L}.toMutableList()
    if(value and GENERAL_CONSENT_MASK.inv()!=0L) labels.add(if(es) "Otros ajustes conservados" else "Other settings preserved")
    return labels.joinToString(", ").ifBlank {if(es) "Sin requisitos adicionales en este dispositivo" else "No additional requirements on this device"}
}
@Composable internal fun GeneralConsentDialog(initialValue:Long,enabled:Boolean,es:Boolean,onDismiss:()->Unit,onApply:(Long)->Boolean) {
    var selected by remember {mutableLongStateOf(initialValue and GENERAL_CONSENT_MASK)}
    var invalid by remember {mutableStateOf(false)}
    fun t(a:String,b:String)=if(es) a else b
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=onDismiss,
        title={Text(t("Consentimiento del usuario","User consent"),fontSize=18.sp,fontWeight=FontWeight.Bold)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Solo se editan requisitos propios del dispositivo. Desmarcar no elimina requisitos heredados del servidor, grupo o usuario. No se muestra aquí el consentimiento efectivo combinado.","Only device-specific requirements are edited. Clearing options does not remove inherited server, group or user requirements. Combined effective consent is not shown here."),style=MaterialTheme.typography.bodySmall)
            generalConsentOptions(es).forEachIndexed {i,label->
                val bit=1L shl i
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(selected and bit!=0L,{checked->selected=if(checked) selected or bit else selected and bit.inv();invalid=false},enabled=enabled,modifier=Modifier.testTag("general_consent_bit_$bit"))
                    Text(label,Modifier.weight(1f))
                }
            }
            Text(t("Se conservan los ajustes desconocidos. Aplicar modifica el borrador local; después revisa y guarda en General.","Unknown settings are preserved. Apply updates the local draft; then review and save in General."),style=MaterialTheme.typography.bodySmall)
            if(!enabled || invalid) Text(t("No se puede aplicar con los datos y permisos actuales.","Cannot apply with current data and permissions."),color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={TextButton(enabled=enabled,onClick={invalid=!onApply(generalConsentMerge(initialValue,selected))},modifier=Modifier.testTag("general_consent_apply")) {Text(t("Aplicar al borrador","Apply to draft"))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}})
}
