package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

internal fun profileReceivedUserGroup(s:ProfileState,id:String):JSONObject? {
    if(!s.ready || s.busy || s.loading || s.detailsBusy || s.userGroupPhase in setOf("loading","failed") || !profileCreatedUserGroupId(s.id,id)) return null
    val records=s.listedUserGroups ?: s.userGroups ?: return null
    return records.singleOrNull {it.optString("_id")==id}
}
@Composable internal fun ReceivedUserGroupDialog(session:ProfileSession,s:ProfileState,id:String,dismissLabel:String?=null,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val group=profileReceivedUserGroup(s,id)
    var query by remember(id) {mutableStateOf("")}
    LaunchedEffect(session,id,s.ready,s.busy,s.userGroupPhase) {if(s.ready && !s.busy && s.userGroupPhase=="idle") session.loadUserGroupDirectory()}
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(t("Grupo de usuarios","User group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Ficha de consulta. La administración sigue en la pestaña Grupos.","Read-only details. Administration remains in the Groups tab."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.detailsBusy || s.userGroupPhase=="loading") LinearProgressIndicator(Modifier.fillMaxWidth())
            if(group==null) Text(t("Ficha no disponible en los datos actuales. Espera la consulta o actualiza; su ausencia no acredita que se haya eliminado.","Entry unavailable in current data. Wait for the query or refresh; absence does not prove deletion."),style=MaterialTheme.typography.bodySmall) else {
                Text(group.optString("name").ifBlank {t("Sin nombre informado","Name not reported")},style=MaterialTheme.typography.titleSmall)
                if(!group.isNull("desc") && group.optString("desc").isNotBlank()) Text(group.optString("desc"))
                if(!group.isNull("membershipType")) Text(t("Gestionado externamente.","Externally managed."),style=MaterialTheme.typography.bodySmall)
                val links=group.optJSONObject("links")
                if(links==null) Text(t("El servidor no ha informado enlaces para esta ficha. No se interpretan como cero miembros o cero accesos.","The server has not reported links for this entry. This does not mean zero members or access assignments."),style=MaterialTheme.typography.bodySmall) else {
                    Text("${links.length()} "+t("enlaces recibidos; no son un cálculo de permisos efectivos.","received links; not a calculation of effective rights."),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar enlace recibido","Search received links"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    val ids=links.keys().asSequence().filter {query.isBlank() || it.contains(query,true) || profileReceivedLinkPresentation(s,group,it,es).label.contains(query,true) || links.optJSONObject(it)?.optString("name")?.contains(query,true)==true}.sorted().toList()
                    if(ids.isEmpty()) Text(t("Sin coincidencias en los enlaces recibidos.","No matches among received links."))
                    Box(Modifier.fillMaxWidth().height(180.dp)) {LazyColumn {items(ids,key={it}) {linkId->
                        Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                            Text(profileReceivedLinkPresentation(s,group,linkId,es).label,style=MaterialTheme.typography.bodySmall)
                            val rights=profileGroupMeshReportedRights(group,linkId)
                            Text(rights?.let {java.lang.String.format(java.util.Locale.ROOT,"rights=0x%08X",it)} ?: t("Derechos no informados","Rights not reported"),style=MaterialTheme.typography.bodySmall)
                        }
                    }}}
                }
            }
            if(s.readError) Text(t("Datos generales incompletos; esta ficha no habilita modificaciones.","General data is incomplete; this entry does not enable changes."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=s.ready && !s.busy && !s.loading && !s.detailsBusy && s.userGroupPhase!="loading",onClick={session.refresh();session.loadUserGroupDirectory()}) {Text(t("Actualizar","Refresh"))}},dismissButton={TextButton(onClick=onDismiss) {Text(dismissLabel ?: t("Volver a Accesos","Back to Access"))}})
}
