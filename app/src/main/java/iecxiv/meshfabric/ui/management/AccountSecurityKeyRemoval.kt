package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray

@Composable internal fun AccountSecurityKeyRemoval(keys:JSONArray?,enabled:Boolean,server:String,actor:String,es:Boolean,onRemove:(Long)->Boolean) {
    key(server,actor) {
        val snapshot=keys?.toString()
        val choices=remember(snapshot) {accountSecurityKeyChoices(snapshot?.let(::JSONArray))}
        var review by remember {mutableStateOf<AccountSecurityKeyChoice?>(null)}
        var submitted by remember(snapshot) {mutableStateOf(false)}
        fun t(a:String,b:String)=if(es) a else b
        fun name(k:AccountSecurityKeyChoice)=k.name.ifBlank {t("Sin nombre","Unnamed")}
        LaunchedEffect(snapshot,enabled) {if(!accountSecurityKeyCanRemove(choices,review,enabled)) review=null}
        when {
            choices==null->Text(t("Lista de llaves no disponible o incompatible. Actualiza antes de eliminar.","Key list unavailable or incompatible. Refresh before removing."),style=MaterialTheme.typography.bodySmall)
            choices.isEmpty()->Text(t("No hay claves registradas.","No registered keys."))
            else->Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
                Column(Modifier.width(IntrinsicSize.Max),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    choices.forEach {item->
                        OutlinedButton(enabled=enabled && !submitted,onClick={review=item},modifier=Modifier.fillMaxWidth().testTag("account_security_key_"+item.id)) {
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text(name(item),modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.titleSmall)
                                Text("ID: "+item.id,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall)
                                Text(t("Seleccionar para eliminar…","Select to remove…"),modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        review?.let {item->ManagementDialog(onDismissRequest={review=null},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),title={Text(t("Eliminar clave de seguridad","Remove security key"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Cuenta: ","Account: ")+actor)
            Text(name(item),style=MaterialTheme.typography.titleSmall)
            Text("ID: "+item.id,style=MaterialTheme.typography.bodySmall)
            Text(t("Se solicitará eliminar únicamente el registro de esta llave en esta cuenta. No se puede deshacer desde este diálogo.","Only this key registration on this account will be requested for removal. This cannot be undone from this dialog."))
            if(choices?.size==1) Text(t("Es la única llave de la lista recibida. Asegúrate de disponer de otra forma de acceso antes de continuar.","This is the only key in the received list. Make sure you have another way to sign in before continuing."),color=MaterialTheme.colorScheme.error)
            Text(t("No se considera eliminada hasta releer la lista del servidor. No hay reenvíos automáticos.","Removal is not confirmed until the server list is reread. No automatic resends."),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={Button(enabled=!submitted && accountSecurityKeyCanRemove(choices,item,enabled),onClick={if(onRemove(item.id)) {submitted=true;review=null}},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),modifier=Modifier.testTag("account_security_key_remove_confirm")) {Text(t("Eliminar esta clave","Remove this key"))}},dismissButton={TextButton(onClick={review=null}) {Text(t("Cancelar","Cancel"))}})}
    }
}
