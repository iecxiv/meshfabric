package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

@Composable internal fun UserMeshAssignmentReview(session:ProfileSession,s:ProfileState,uid:String,mid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    fun mask(v:Long?)=v?.let {java.lang.String.format(java.util.Locale.ROOT,"0x%08X",it)} ?: t("No informado","Not reported")
    val server=session.broadcastServerLabel()
    val actor=remember {s.id};val beforeServer=remember {server}
    val currentUser=s.users.singleOrNull {it.optString("_id")==uid}
    val currentMesh=s.groups.singleOrNull {it.optString("_id")==mid}
    var frozenUser by remember {mutableStateOf<JSONObject?>(null)}
    var frozenMesh by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val review=frozenUser!=null && frozenMesh!=null
    val user=frozenUser ?: currentUser;val mesh=frozenMesh ?: currentMesh
    val identityA=profileReviewIdentity(user,s.users,es)
    val identityB=profileReviewIdentity(mesh,s.groups,es)
    val identityReady=identityA.usable && identityB.usable
    var identityInvalidated by remember {mutableStateOf(false)}
    LaunchedEffect(review,identityReady) {if(review && !identityReady) {identityInvalidated=true;approved=false}}
    val packet=if(review) runCatching {profileUserMeshRevokePacket(actor,beforeServer,requireNotNull(frozenUser),requireNotNull(frozenMesh),approved)}.getOrNull() else null
    val candidate=if(currentUser!=null && currentMesh!=null) profileUserMeshRevokePacket(actor,beforeServer,currentUser,currentMesh,true) else null
    val canPrepare=identityReady && !submitted && candidate!=null && profileUserMeshRevokeAllowed(s,candidate,server)
    val reviewCurrent=identityReady && !identityInvalidated && packet!=null && profileUserMeshRevokeAllowed(s,JSONObject(packet.toString()).put("_directConfirmed",true),server)
    val canSend=identityReady && !identityInvalidated && !submitted && packet!=null && profileUserMeshRevokeAllowed(s,packet,server)
    val observed=submitted && s.notice=="userMeshRevokedObserved" && s.revokedDirectUser==uid && s.revokedDirectMesh==mid
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Revisar acceso directo","Review direct access"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Usuario: ","User: ")+identityA.label)
            Text(t("Grupo de dispositivos: ","Device group: ")+identityB.label)
            if(!identityReady || identityInvalidated) Text(t("No se puede confirmar: falta un nombre o los datos visibles no distinguen inequívocamente los destinos. Cierra y actualiza la ficha.","Cannot confirm: a name is missing or visible information does not uniquely identify the targets. Close and refresh the details."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(review) Text(t("Datos congelados para esta revisión.","Data frozen for this review."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.detailsBusy || (submitted && s.busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(t("Enlace recibido en el usuario: ","Link received in the user: ")+mask(profileGroupMeshReportedRights(user,mid)))
            Text(t("Enlace recibido en el grupo: ","Link received in the group: ")+mask(profileGroupMeshReportedRights(mesh,uid)))
            Text(t("Derechos directos del actor: ","Direct actor rights: ")+mask(profileGroupMeshReportedRights(mesh,actor)))
            Text(t("Retira esta asignación directa completa, incluidos los bits desconocidos. No borra el usuario ni el grupo y no retira accesos heredados u otras asignaciones. No demuestra pérdida de todos los accesos efectivos.","Removes this complete direct assignment, including unknown bits. Does not delete the user or group or remove inherited access or other assignments. Does not establish loss of all effective access."),style=MaterialTheme.typography.bodySmall)
            Text(t("Las lecturas y el envío no son atómicos. El servidor valida el estado existente al recibir la solicitud.","Reads and sending are not atomic. The server validates the state present when it receives the request."),style=MaterialTheme.typography.bodySmall)
            if(review && !submitted) Row {Checkbox(approved,{approved=it},enabled=reviewCurrent,modifier=Modifier.testTag("direct-revoke-consent"));Text(t("Confirmo retirar el acceso directo de este usuario a este grupo.","I confirm removing this user's direct access to this group."),Modifier.weight(1f))}
            if(observed) Text(t("Respuesta confirmada y ambos enlaces directos observados ausentes en consultas posteriores.","Response confirmed and both direct links observed absent in subsequent reads."),style=MaterialTheme.typography.titleSmall)
            else if(submitted) Text(when(s.notice) {"rejected"->t("El servidor rechazó la solicitud.","The server rejected the request.");"unknown"->t("Resultado sin confirmar. Comprueba el estado antes de repetir.","Outcome unconfirmed. Check state before repeating.");else->t("Solicitud enviada; resultado todavía sin verificar.","Request sent; outcome not verified yet.")})
            else if(error || (review && !reviewCurrent)) Text(t("Esta copia no permite enviar. Vuelve atrás y actualiza los datos y permisos.","This copy cannot be sent. Go back and refresh data and permissions."),color=MaterialTheme.colorScheme.error)
            Text(t("No hay reintentos automáticos. Cerrar no cancela una solicitud ya enviada.","No automatic retries. Closing does not cancel an already sent request."),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={if(!submitted) Button(modifier=Modifier.testTag(if(review) "direct-revoke-send" else "direct-revoke-prepare"),enabled=if(review) canSend else canPrepare,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={
        if(!review) {if(canPrepare && currentUser!=null && currentMesh!=null) {frozenUser=JSONObject(currentUser.toString());frozenMesh=JSONObject(currentMesh.toString());approved=false;error=false}}
        else if(packet!=null && session.submitUserMeshRevoke(packet)) {submitted=true;approved=false} else error=true
    }) {Text(if(review) t("Retirar acceso directo","Remove direct access") else t("Preparar retirada","Prepare removal"))}},dismissButton={Row {
        if(review && !submitted) TextButton(onClick={frozenUser=null;frozenMesh=null;approved=false;error=false;session.refresh()}) {Text(t("Volver y actualizar","Back and refresh"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
