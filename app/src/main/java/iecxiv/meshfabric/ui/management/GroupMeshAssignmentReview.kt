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

internal fun profileGroupMeshReportedRights(record:JSONObject?,id:String):Long?=runCatching {
    val value=record?.optJSONObject("links")?.optJSONObject(id)?.opt("rights")
    require(value is Number)
    userAdvancedMask(value)
}.getOrNull()
internal fun profileGroupMeshRevokePacket(actor:String,server:String,group:JSONObject,mesh:JSONObject,confirmed:Boolean=false)=JSONObject().put("action","removemeshuser").put("meshid",mesh.getString("_id")).put("userid",group.getString("_id")).put("_meshActor",actor).put("_meshServer",server).put("_meshGroupBefore",profileUserGroupDeleteSnapshot(group)).put("_meshDeviceBefore",profileUserGroupDeleteSnapshot(mesh)).put("_meshConfirmed",confirmed)
internal fun profileGroupMeshReviewAllowed(s:ProfileState,gid:String,mid:String,server:String):Boolean=runCatching {
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    profileGroupMeshRevokeAllowed(s,profileGroupMeshRevokePacket(s.id,server,group,mesh,true),server)
}.getOrDefault(false)
internal fun profileGroupMeshReadbackReady(s:ProfileState,gid:String,mid:String,ack:Boolean,groupRead:Boolean,deviceRead:Boolean)=ack && groupRead && deviceRead && profileGroupMeshRevokeObserved(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid},s.groups.singleOrNull {it.optString("_id")==mid},gid,mid,true)
@Composable internal fun GroupMeshAssignmentReview(session:ProfileSession,s:ProfileState,gid:String,mid:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    fun mask(value:Long?)=value?.let {java.lang.String.format(java.util.Locale.ROOT,"0x%08X",it)} ?: t("No informado","Not reported")
    val server=session.broadcastServerLabel()
    val actor=remember {s.id}
    val beforeServer=remember {server}
    val currentGroup=s.listedUserGroups?.singleOrNull {it.optString("_id")==gid}
    val currentMesh=s.groups.singleOrNull {it.optString("_id")==mid}
    var frozenGroup by remember {mutableStateOf<JSONObject?>(null)}
    var frozenMesh by remember {mutableStateOf<JSONObject?>(null)}
    var approved by remember {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val review=frozenGroup!=null && frozenMesh!=null
    val group=frozenGroup ?: currentGroup
    val mesh=frozenMesh ?: currentMesh
    val identityA=profileReviewIdentity(group,s.listedUserGroups.orEmpty(),es)
    val identityB=profileReviewIdentity(mesh,s.groups,es)
    val identityReady=identityA.usable && identityB.usable
    var identityInvalidated by remember {mutableStateOf(false)}
    LaunchedEffect(review,identityReady) {if(review && !identityReady) {identityInvalidated=true;approved=false}}
    val packet=if(review) runCatching {profileGroupMeshRevokePacket(actor,beforeServer,requireNotNull(frozenGroup),requireNotNull(frozenMesh),approved)}.getOrNull() else null
    val canPrepare=identityReady && !submitted && actor==s.id && server==beforeServer && profileGroupMeshReviewAllowed(s,gid,mid,server)
    val reviewCurrent=identityReady && !identityInvalidated && packet!=null && profileGroupMeshRevokeAllowed(s,JSONObject(packet.toString()).put("_meshConfirmed",true),server)
    val canSend=identityReady && !identityInvalidated && !submitted && packet!=null && profileGroupMeshRevokeAllowed(s,packet,server)
    val observed=submitted && s.notice=="groupMeshRevokedObserved" && s.revokedGroup==gid && s.revokedDeviceGroup==mid
    LaunchedEffect(session,gid,mid,s.ready,s.busy,s.userGroupPhase) {if(s.ready && !s.busy && s.userGroupPhase=="idle") session.loadUserGroupDirectory()}
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Revisar asignación","Review assignment"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo de usuarios: ","User group: ")+identityA.label)
            Text(t("Grupo de dispositivos: ","Device group: ")+identityB.label)
            if(!identityReady || identityInvalidated) Text(t("No se puede confirmar: falta un nombre o los datos visibles no distinguen inequívocamente los destinos. Cierra y actualiza la ficha.","Cannot confirm: a name is missing or visible information does not uniquely identify the targets. Close and refresh the details."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(review) Text(t("Datos congelados para esta revisión.","Data frozen for this review."),style=MaterialTheme.typography.bodySmall)
            if(s.loading || s.userGroupPhase=="loading" || (submitted && s.busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(t("Enlace recibido en el grupo de usuarios: ","Link received in the user group: ")+mask(profileGroupMeshReportedRights(group,mid)))
            Text(t("Enlace recibido en el grupo de dispositivos: ","Link received in the device group: ")+mask(profileGroupMeshReportedRights(mesh,gid)))
            Text(t("Derechos directos recibidos del actor: ","Received direct actor rights: ")+mask(profileGroupMeshReportedRights(mesh,actor)))
            Text(t("Retirar elimina esta asignación completa, incluidos sus bits desconocidos. Puede cambiar acceso y políticas heredadas de los miembros. No borra cuentas ni grupos ni modifica otras asignaciones.","Removal deletes this complete assignment, including unknown bits. Members' inherited access and policies may change. Accounts, groups and other assignments are not deleted or edited."),style=MaterialTheme.typography.bodySmall)
            Text(t("La lectura y comprobación no son atómicas: el servidor procesa el estado que exista al recibir la solicitud. No se calculan todos los permisos efectivos ni se deduce autoridad heredada o excepciones de administrador global.","Reads and checks are not atomic: the server processes the state present when it receives the request. All effective rights, inherited authority and global-administrator exceptions are not inferred."),style=MaterialTheme.typography.bodySmall)
            if(review && !submitted) ProfileAlignedCheckboxRow {Checkbox(approved,{approved=it},enabled=reviewCurrent,modifier=Modifier.testTag("group-revoke-consent"));Text(t("Confirmo retirar esta asignación entre estos dos grupos.","I confirm removing this assignment between these two groups."),Modifier.weight(1f))}
            if(observed) Text(t("Respuesta confirmada y ambos enlaces observados ausentes en consultas posteriores. No acredita pérdida de todos los accesos efectivos.","Response confirmed and both links observed absent in subsequent reads. This does not establish loss of all effective access."),style=MaterialTheme.typography.titleSmall)
            else if(submitted) Text(if(s.notice=="rejected") t("El servidor rechazó la solicitud.","The server rejected the request.") else t("Solicitud enviada; resultado todavía sin verificar.","Request sent; outcome not verified yet."),style=MaterialTheme.typography.bodySmall)
            else if(error || (review && !reviewCurrent)) Text(t("No se puede enviar con esta copia. Vuelve atrás y actualiza o revisa los cambios.","Cannot send this copy. Go back and refresh or review changes."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(t("No hay reintentos automáticos. Cerrar no revoca una solicitud enviada. Si el resultado queda sin confirmar, consulta el estado antes de repetir.","No automatic retries. Closing does not revoke a sent request. If unconfirmed, check state before repeating."),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={if(!submitted) Button(modifier=Modifier.testTag(if(review) "group-revoke-send" else "group-revoke-prepare"),enabled=if(review) canSend else canPrepare,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={if(!review) {val g=currentGroup;val m=currentMesh;if(g!=null && m!=null) {frozenGroup=JSONObject(g.toString());frozenMesh=JSONObject(m.toString());approved=false;error=false}} else if(packet!=null && session.submit(packet)) {submitted=true;approved=false} else error=true}) {Text(if(review) t("Retirar asignación","Remove assignment") else t("Preparar retirada","Prepare removal"))}},dismissButton={Row {if(review && !submitted) TextButton(onClick={frozenGroup=null;frozenMesh=null;approved=false;error=false;session.refresh();session.loadUserGroupDirectory()}) {Text(t("Volver y actualizar","Back and refresh"))};TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}}})
}
