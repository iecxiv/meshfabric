package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

@Composable internal fun UserContactEditor(session:ProfileSession,s:ProfileState,user:JSONObject,onDismiss:()->Unit,onSaved:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val id=user.optString("_id")
    val initial=remember(id) { runCatching { userContactSnapshot(user) }.getOrNull() }
    if(initial==null) {
        ManagementDialog(onDismissRequest=onDismiss,title={Text(t("Datos incompatibles","Incompatible data"))},text={Text(t("Actualiza la ficha antes de editar el contacto.","Refresh the details before editing contact information."))},confirmButton={TextButton(onClick=onDismiss){Text(t("Cerrar","Close"))}})
        return
    }
    var field by remember(id) { mutableStateOf("phone") }
    var phone by remember(id) { mutableStateOf(initial.getString("phone")) }
    var verified by remember(id) { mutableStateOf(initial.getBoolean("emailVerified")) }
    var approved by remember(id) { mutableStateOf(false) }
    var feedback by remember(id) { mutableStateOf("") }
    val email=initial.getString("email")
    val supportsVerification=s.server?.opt("emailcheck")==true
    val packet=userContactPacket(id,initial,field,if(field=="phone") phone else verified)
    val current=s.users.firstOrNull { it.optString("_id")==id }
    val allowed=userContactAllowed(s,current,packet)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=900.dp).fillMaxWidth(0.96f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(t("Contacto y estado del correo","Contact and email status"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.72f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(user.optString("name"),style=MaterialTheme.typography.titleSmall)
            Text(id,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(field=="phone",{field="phone";approved=false},enabled=s.canWrite);Text(t("Teléfono","Phone")) }
            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(field=="emailVerified",{field="emailVerified";approved=false},enabled=s.canWrite && supportsVerification);Text(t("Estado de verificación del correo","Email verification status")) }
            if(field=="phone") {
                OutlinedTextField(phone,{phone=it;approved=false},label={Text(t("Teléfono","Phone"))},singleLine=true,enabled=s.canWrite,isError=!userPhoneInputAllowed(phone),modifier=Modifier.fillMaxWidth())
                Text(t("Vacío elimina el teléfono. Para introducir uno nuevo, usa entre 5 y 17 dígitos con + opcional, sin espacios. El servidor debe confirmar el valor.","Leave empty to remove the phone. Enter 5 to 17 digits with an optional +, without spaces. The server must confirm the value."),style=MaterialTheme.typography.bodySmall)
                Text(t("Modificar o eliminar el teléfono puede afectar al segundo factor por SMS. No se envía ningún SMS ni código de comprobación desde este diálogo.","Changing or removing the phone may affect SMS second-factor authentication. This dialog sends no SMS or verification code."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            } else {
                Text(email.ifBlank { t("Sin correo electrónico","No email address") })
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(verified,{verified=it;approved=false},enabled=s.canWrite && supportsVerification && (email.isNotBlank() || verified));Text(t("Correo marcado como verificado","Email marked as verified"),Modifier.weight(1f)) }
                Text(t("Esta acción cambia el estado administrativo registrado en MeshCentral. No envía un correo de verificación ni demuestra acceso al buzón.","This changes the administrative status recorded in MeshCentral. It neither sends a verification email nor proves access to the mailbox."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
            if(!supportsVerification) Text(t("El servidor no ha anunciado comprobación de correo; su estado no se puede editar aquí.","The server has not announced email checking; its status cannot be edited here."),style=MaterialTheme.typography.bodySmall)
            if(!allowed) Text(t("Sin cambios válidos, permisos insuficientes o ficha desactualizada. Si los datos cambiaron, cierra y actualiza antes de continuar.","No valid change, insufficient rights or outdated details. Close and refresh if the data changed."),style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(approved,{approved=it},enabled=s.canWrite);Text(t("Confirmo este cambio para el usuario indicado.","I confirm this change for the indicated user."),Modifier.weight(1f)) }
            if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=allowed && approved,onClick={if(session.submit(packet)) onSaved() else feedback=t("No se ha enviado el cambio.","The change was not sent.")}){Text(t("Aplicar","Apply"))}},dismissButton={TextButton(onClick=onDismiss){Text(t("Cancelar","Cancel"))}})
}
